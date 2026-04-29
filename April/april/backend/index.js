const http = require('http');
const { Server } = require('socket.io');
const dgram = require('dgram'); // For UDP Discovery

// MISSING IMPORTS - ADDED
const express = require('express');
const cors = require('cors');
const mysql = require('mysql2/promise');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const os = require('os');
const path = require('path');
const multer = require('multer');
const fs = require('fs');
require('dotenv').config();

// Ensure uploads folder exists
const uploadDir = 'public/uploads';
if (!fs.existsSync(uploadDir)){
    fs.mkdirSync(uploadDir, { recursive: true });
}

// Multer Storage for Profile Photos
const storage = multer.diskStorage({
    destination: (req, file, cb) => cb(null, uploadDir),
    filename: (req, file, cb) => cb(null, `profile_${Date.now()}${path.extname(file.originalname)}`)
});
const upload = multer({ storage: storage });

// ADD THESE TWO LINES FOR ARDUINO COM PORT
const { SerialPort } = require('serialport');
const { ReadlineParser } = require('@serialport/parser-readline');

let port; // Global SerialPort instance
let isFlashMode = false; // Flag to release port for firmware uploads
let flashModeTimer = null;

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});
app.use(cors());
app.use(express.json());
app.use((err, req, res, next) => {
    if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
        console.error('[BODY_ERROR]', err.message);
        return res.status(400).send({ success: false, message: 'Invalid JSON payload' });
    }
    next();
});
// HIDDEN MANAGEMENT ROUTES (Moved up for priority)
app.get('/admin', (req, res) => res.sendFile(path.join(__dirname, 'public/management.html')));
app.get('/admin.html', (req, res) => res.sendFile(path.join(__dirname, 'public/management.html')));
app.get('/management', (req, res) => res.sendFile(path.join(__dirname, 'public/management.html')));
app.get('/dashboard.html/admin', (req, res) => res.sendFile(path.join(__dirname, 'public/management.html')));

app.use(express.static('public'));

app.get('/api/health', async (req, res) => {
    const db = { ready: dbReady };

    if (dbReady && pool && pool.query) {
        try {
            const [rows] = await pool.query('SELECT 1 AS ok');
            db.ok = rows?.[0]?.ok === 1;
        } catch (err) {
            db.ok = false;
            db.error = err.message;
        }
    }

    res.json({
        success: true,
        service: 'vitals-backend',
        commit: process.env.RAILWAY_GIT_COMMIT_SHA || process.env.RAILWAY_DEPLOYMENT_ID || 'local',
        db
    });
});

const dbConfig = {
  host: process.env.DB_HOST || process.env.MYSQLHOST || 'localhost',
  port: Number(process.env.DB_PORT || process.env.MYSQLPORT || 3306),
  user: process.env.DB_USER || process.env.MYSQLUSER || 'root',
  password: process.env.DB_PASSWORD || process.env.MYSQLPASSWORD || '',
  database: process.env.DB_NAME || process.env.MYSQLDATABASE || 'vitals_db',
};

const JWT_SECRET = process.env.JWT_SECRET || 'your_secret_key_change_this';

let pool;
let dbReady = false;
let pendingCommands = [];
let activeDevices = new Map(); // Track Socket.io IDs for hardware
let hardwareLogs = []; // Buffer for the [Window 4] Hardware Console (In-memory fallback)

// --- [RELAY MODE] 5-SECOND AGGREGATOR ---
let sensorBuffer = {
    heart_rate: [],
    spo2: [],
    body_temp: [],
    last_broadcast: Date.now()
};

function aggregateVitals() {
    const data = {
        heart_rate: 0,
        spo2: 0,
        body_temp: 0,
        timestamp: new Date().toISOString(),
        status: "ACTIVE"
    };

    if (sensorBuffer.heart_rate.length > 0) {
        data.heart_rate = Math.round(sensorBuffer.heart_rate.reduce((a, b) => a + b, 0) / sensorBuffer.heart_rate.length);
    }
    if (sensorBuffer.spo2.length > 0) {
        data.spo2 = Math.round(sensorBuffer.spo2.reduce((a, b) => a + b, 0) / sensorBuffer.spo2.length);
    }
    if (sensorBuffer.body_temp.length > 0) {
        data.body_temp = parseFloat((sensorBuffer.body_temp.reduce((a, b) => a + b, 0) / sensorBuffer.body_temp.length).toFixed(2));
    }

    if (data.heart_rate > 0 || data.spo2 > 0 || data.body_temp > 0) {
        console.log(`[RELAY] 10s Clinical Broadcast: HR:${data.heart_rate} SpO2:${data.spo2}% Temp:${data.body_temp}C`);
        io.emit('vitals_update', data);
    }

    sensorBuffer.heart_rate = [];
    sensorBuffer.spo2 = [];
    sensorBuffer.body_temp = [];
}

// --- [V9.1] MEMORY PROTECTION ENGINE ---
function memoryManagementCycle() {
    const now = Date.now();
    
    // 1. Flush Transient Console Logs (Keep only last 20 for UI responsiveness)
    if (hardwareLogs.length > 20) {
        hardwareLogs = hardwareLogs.slice(-20);
    }

    // 2. Clear stale aggregated data if no broadcast happened for 30s
    // (Increased to 30s because ESP32/App now use a 10s high-fidelity window)
    if (now - sensorBuffer.last_broadcast > 30000) {
        sensorBuffer.heart_rate = [];
        sensorBuffer.spo2 = [];
        sensorBuffer.body_temp = [];
        sensorBuffer.last_broadcast = now;
    }

    // 3. Force small GC hint (if --expose-gc is used, but works even without it by clearing references)
    if (global.gc) {
        global.gc();
    }
}
setInterval(aggregateVitals, 10000); // 🛡️ [SYNCED] 10-second high-fidelity window
setInterval(memoryManagementCycle, 10000); // 🚀 10s Memory Release Cycle


// Helper: Persistent Logging to MySQL
async function logHardwareEvent(message, level = 'INFO', sessionId = null) {
    const timestamp = new Date();
    // 1. In-memory for instant dashboard polling if sockets fail
    hardwareLogs.push({
        timestamp: timestamp.toLocaleTimeString(),
        type: level,
        message: message
    });
    if (hardwareLogs.length > 100) hardwareLogs.shift();

    // 2. Persistent storage
    if (pool && pool.query) {
        try {
            await pool.query(
                "INSERT INTO hardware_logs (message, level, session_id) VALUES (?, ?, ?)",
                [message, level, sessionId]
            );
        } catch (err) {
            console.error('[DB_LOG_ERR]', err.message);
        }
    }

    // 3. Real-time push
    io.emit('hardware_log', {
        timestamp: timestamp.toLocaleTimeString(),
        level,
        message
    });

    // Notify apps about hub status if this is data arriving
    if (level === 'DATA' || level === 'SUCCESS') {
        io.emit('hub_status', { connected: true });
    }
}

// Middleware: Verify JWT Token
const verifyToken = (req, res, next) => {
    const token = req.headers['authorization']?.split(' ')[1];
    if (!token) return res.status(403).json({ message: 'No token provided' });
    
    jwt.verify(token, JWT_SECRET, (err, decoded) => {
        if (err) return res.status(401).json({ message: 'Invalid token' });
        req.user = decoded;
        next();
    });
};

// Middleware: Verify Admin Role
const verifyAdmin = (req, res, next) => {
    const userRole = (req.user.role || '').toLowerCase();
    
    if (userRole !== 'admin') {
        console.warn(`[AUTH_WARN] Access Denied for ${req.user.username} (Role: ${userRole})`);
        return res.status(403).json({ message: 'Admin access required' });
    }
    next();
};

// Authentication Endpoints

// Register
app.post('/api/auth/register', async (req, res) => {
    if (!dbReady || !pool || !pool.query) {
        return res.status(503).json({ success: false, message: 'Database is not connected. Check Railway MySQL variables and redeploy.' });
    }

    const { email, password, role, age, gender, stroke_duration } = req.body;
    const username = req.body.username || req.body.name;
    
    if (!username || !email || !password) {
        return res.status(400).json({ message: 'All fields are required' });
    }

    try {
        const finalRole = role || 'Patient';
        const [idRows] = await pool.query('SELECT COALESCE(MAX(id), 0) + 1 AS nextId FROM users');
        const nextId = idRows[0]?.nextId || 1;

        await pool.query(
            'INSERT INTO users (id, username, email, password, role, age, gender, stroke_duration) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
            [nextId, username, email, password, finalRole, age || null, gender || null, stroke_duration || null]
        );
        
        // Notify Hub Dashboard in real-time
        if (typeof io !== 'undefined') {
            io.emit('user_registered', { 
                username, 
                email, 
                role: finalRole,
                timestamp: new Date()
            });
        }
        
        res.json({ message: 'User registered successfully', success: true });
    } catch (err) {
        console.error('[SIGNUP_ERROR]', err);
        if (err.code === 'ER_DUP_ENTRY') {
            res.status(400).json({ success: false, message: 'This email is already registered.' });
        } else {
            res.status(500).json({ success: false, message: 'Server error during registration.' });
        }
    }
});

// Login
app.post('/api/auth/login', async (req, res) => {
    if (!dbReady || !pool || !pool.query) {
        return res.status(503).json({ success: false, message: 'Database is not connected. Check Railway MySQL variables and redeploy.' });
    }

    const username = req.body.username || req.body.email;
    const password = req.body.password;
    
    if (!username || !password) {
        return res.status(400).json({ message: 'Username/Email and password required' });
    }

    try {
        const result = await pool.query('SELECT * FROM users WHERE username = ? OR email = ?', [username, username]);
        const rows = Array.isArray(result) ? result[0] : result;
        
        if (!rows || rows.length === 0) {
            const allowOfflineLogin = process.env.OFFLINE_LOGIN === 'true';
            if (allowOfflineLogin && username === 'admin' && password === 'admin123') {
                const offlineUser = {
                    id: 1,
                    username: 'admin',
                    email: 'admin@vitalsmonitor.local',
                    role: 'admin'
                };
                const token = jwt.sign(
                    { id: offlineUser.id, username: offlineUser.username, role: offlineUser.role },
                    JWT_SECRET,
                    { expiresIn: '24h' }
                );

                console.log('[AUTH] Offline admin login accepted.');
                return res.json({ token, user: offlineUser });
            }

            console.log(`[LOGIN_FAILED] User not found: ${username}`);
            return res.status(401).json({ message: 'Invalid credentials' });
        }

        const user = rows[0];
        console.log(`[LOGIN_ATTEMPT] User: ${user.username}, Received Pass Length: ${password.length}, DB Pass Length: ${user.password.length}`);

        if (password !== user.password) {
            console.log(`[LOGIN_FAILED] Password mismatch for ${username}`);
            return res.status(401).json({ message: 'Invalid credentials' });
        }

        const token = jwt.sign(
            { id: user.id, username: user.username, role: user.role },
            JWT_SECRET,
            { expiresIn: '24h' }
        );

        res.json({
            success: true,
            token,
            user: {
                id: user.id,
                username: user.username,
                email: user.email,
                role: user.role,
                age: user.age || '',
                gender: user.gender || '',
                stroke_duration: user.stroke_duration || ''
            }
        });
    } catch (err) {
        console.error('[LOGIN_ERROR]', err);
        res.status(500).json({ message: 'Server error' });
    }
});

// Update Profile
app.put('/api/auth/profile', verifyToken, async (req, res) => {
    const { email, password } = req.body;
    const userId = req.user.id;

    try {
        if (password) {
            await pool.query('UPDATE users SET email = ?, password = ? WHERE id = ?', [email, password, userId]);
        } else {
            await pool.query('UPDATE users SET email = ? WHERE id = ?', [email, userId]);
        }
        
        res.json({ success: true, message: 'Profile updated' });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: 'Error updating profile' });
    }
});

app.get('/api/auth/me', verifyToken, async (req, res) => {
    try {
        const [rows] = await pool.query(
            'SELECT id, username, email, role, age, gender, stroke_duration FROM users WHERE id = ?',
            [req.user.id]
        );
        if (rows.length === 0) return res.status(404).json({ success: false, message: 'User not found' });
        res.json({ success: true, user: rows[0] });
    } catch (err) {
        console.error('[PROFILE_ME_ERROR]', err);
        res.status(500).json({ success: false, message: 'Error fetching profile' });
    }
});

// Profile Photo Upload (Patient Accounts)
app.post('/api/auth/profile-photo', verifyToken, upload.single('photo'), async (req, res) => {
    if (!req.file) return res.status(400).json({ message: 'No photo provided' });
    
    console.log(`\x1b[35m[HUB] Profile Photo Uploaded: ${req.file.filename}\x1b[0m`);
    res.json({ 
        success: true, 
        message: 'Photo uploaded', 
        url: `/uploads/${req.file.filename}` 
    });
});

// Get All Users (Admin only)
app.get('/api/admin/users', verifyToken, verifyAdmin, async (req, res) => {
    try {
        const result = await pool.query('SELECT id, username, email, role, created_at FROM users ORDER BY created_at DESC');
        const rows = Array.isArray(result) ? result[0] : result;
        res.json(rows);
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: 'Error fetching users' });
    }
});

// Get Single User (Admin only)
app.get('/api/admin/users/:id', verifyToken, verifyAdmin, async (req, res) => {
    try {
        const result = await pool.query('SELECT id, username, email, role FROM users WHERE id = ?', [req.params.id]);
        const rows = Array.isArray(result) ? result[0] : result;
        if (!rows || rows.length === 0) {
            return res.status(404).json({ message: 'User not found' });
        }
        res.json(rows[0]);
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: 'Error fetching user' });
    }
});

// Create User (Admin only)
app.post('/api/admin/users', verifyToken, verifyAdmin, async (req, res) => {
    const { username, email, password, role } = req.body;
    
    if (!username || !email || !password) {
        return res.status(400).json({ message: 'All fields are required' });
    }

    try {
        await pool.query(
            'INSERT INTO users (username, email, password, role) VALUES (?, ?, ?, ?)',
            [username, email, password, role || 'technician']
        );
        
        res.json({ message: 'User created successfully', success: true });
    } catch (err) {
        console.error(err);
        res.status(400).json({ message: 'Error creating user' });
    }
});

// Update User (Admin only)
app.put('/api/admin/users/:id', verifyToken, verifyAdmin, async (req, res) => {
    const { username, email, password, role } = req.body;
    const userId = req.params.id;

    try {
        if (password) {
            await pool.query(
                'UPDATE users SET username = ?, email = ?, password = ?, role = ? WHERE id = ?',
                [username, email, password, role, userId]
            );
        } else {
            await pool.query(
                'UPDATE users SET username = ?, email = ?, role = ? WHERE id = ?',
                [username, email, role, userId]
            );
        }
        
        res.json({ success: true, message: 'User updated' });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: 'Error updating user' });
    }
});

// Delete User (Admin only)
app.delete('/api/admin/users/:id', verifyToken, verifyAdmin, async (req, res) => {
    try {
        await pool.query('DELETE FROM users WHERE id = ?', [req.params.id]);
        res.json({ success: true, message: 'User deleted' });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: 'Error deleting user' });
    }
});

// Existing Endpoints
app.get('/api/admin/sensors', verifyToken, async (req, res) => {
    try {
        const result = await pool.query('SELECT * FROM sensors ORDER BY sensor_type ASC');
        const rows = Array.isArray(result) ? result[0] : result;
        res.json(rows);
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false, message: 'Server error' });
    }
});

// Create Sensor (Admin only)
app.post('/api/admin/sensors', verifyToken, verifyAdmin, async (req, res) => {
    const { sensor_type, pin_assignment } = req.body;
    if (!sensor_type) return res.status(400).json({ message: 'Sensor type is required' });
    
    console.log(`[ADMIN_SENSOR] Creating new sensor: ${sensor_type} (${pin_assignment || 'Auto'})`);
    try {
        await pool.query(
            'INSERT INTO sensors (sensor_type, pin_assignment, status) VALUES (?, ?, ?)',
            [sensor_type, pin_assignment || 'Auto', 'OFFLINE']
        );
        res.json({ success: true, message: 'Sensor registered successfully' });
    } catch (err) {
        console.error(`[ADMIN_SENSOR_ERROR] Error creating sensor: ${err.message}`);
        res.status(500).json({ message: 'Error creating sensor' });
    }
});

// Update Sensor (Admin only)
app.put('/api/admin/sensors/:id', verifyToken, verifyAdmin, async (req, res) => {
    const { sensor_type, pin_assignment } = req.body;
    const sensorId = req.params.id;
    
    console.log(`[ADMIN_SENSOR] Updating sensor ID ${sensorId}: ${sensor_type}, ${pin_assignment}`);
    try {
        await pool.query(
            'UPDATE sensors SET sensor_type = ?, pin_assignment = ? WHERE id = ?',
            [sensor_type, pin_assignment, sensorId]
        );
        res.json({ success: true, message: 'Sensor updated successfully' });
    } catch (err) {
        console.error(`[ADMIN_SENSOR_ERROR] Error updating sensor: ${err.message}`);
        res.status(500).json({ message: 'Error updating sensor' });
    }
});

// --- USER MANAGEMENT ---
app.get('/api/admin/users', verifyToken, async (req, res) => {
    try {
        const [rows] = await pool.query('SELECT id, username, email, role, password, created_at FROM users ORDER BY created_at DESC');
        res.json(rows);
    } catch (err) {
        res.status(500).json({ success: false, message: err.message });
    }
});

app.put('/api/admin/users/:id', verifyToken, async (req, res) => {
    try {
        const { password, role } = req.body;
        await pool.query('UPDATE users SET password = ?, role = ? WHERE id = ?', [password, role, req.params.id]);
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ success: false, message: err.message });
    }
});

app.delete('/api/admin/users/:id', verifyToken, async (req, res) => {
    try {
        await pool.query('DELETE FROM users WHERE id = ?', [req.params.id]);
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ success: false, message: err.message });
    }
});

// Get Registered Users (Admin)
app.get('/api/admin/users', verifyToken, verifyAdmin, async (req, res) => {
    try {
        const [rows] = await pool.query('SELECT id, username, email, role, password, created_at FROM users ORDER BY created_at DESC');
        res.json(rows);
    } catch (err) {
        console.error('[ADMIN_USERS_ERROR]', err);
        res.status(500).json({ success: false, message: 'Error fetching users' });
    }
});

// Update User (Admin)
app.put('/api/admin/users/:id', verifyToken, verifyAdmin, async (req, res) => {
    const { password, role } = req.body;
    try {
        await pool.query('UPDATE users SET password = ?, role = ? WHERE id = ?', [password, role, req.params.id]);
        res.json({ success: true, message: 'User updated successfully' });
    } catch (err) {
        console.error('[ADMIN_USER_UPDATE_ERROR]', err);
        res.status(500).json({ success: false, message: 'Error updating user' });
    }
});

// Delete User (Admin)
app.delete('/api/admin/users/:id', verifyToken, verifyAdmin, async (req, res) => {
    try {
        await pool.query('DELETE FROM users WHERE id = ?', [req.params.id]);
        res.json({ success: true, message: 'User deleted' });
    } catch (err) {
        console.error('[ADMIN_USER_DELETE_ERROR]', err);
        res.status(500).json({ success: false, message: 'Error deleting user' });
    }
});

// Get System Stats (Admin)
app.get('/api/admin/stats', verifyToken, verifyAdmin, async (req, res) => {
    try {
        const [userCount] = await pool.query('SELECT COUNT(*) as count FROM users');
        const [sensorCount] = await pool.query('SELECT COUNT(*) as count FROM sensors');
        const [vitalCount] = await pool.query('SELECT COUNT(*) as count FROM vitals');
        const [activeSessionCount] = await pool.query("SELECT COUNT(*) as count FROM monitoring_sessions WHERE status != 'COMPLETED'");
        
        res.json({
            users: userCount[0].count,
            sensors: sensorCount[0].count,
            vitals: vitalCount[0].count,
            active_sessions: activeSessionCount[0].count,
            uptime: Math.floor(process.uptime()),
            hostname: os.hostname(),
            timestamp: new Date()
        });
    } catch (err) {
        console.error('[ADMIN_STATS_ERROR]', err);
        res.status(500).json({ success: false, message: 'Error fetching stats' });
    }
});

// Save Vital Reading (Admin)
app.post('/api/admin/vitals', verifyToken, async (req, res) => {
    const { sensor_type, metric, value, unit } = req.body;
    
    if (!sensor_type || !metric || value === undefined) {
        return res.status(400).json({ message: 'Missing sensor data' });
    }

    try {
        await pool.query(
            'INSERT INTO vitals (sensor_type, metric, value, unit) VALUES (?, ?, ?, ?)',
            [sensor_type, metric, value, unit || '']
        );
        res.json({ success: true, message: 'Reading saved' });
    } catch (err) {
        console.error(`[VITALS_ERROR] Error saving vitals: ${err.message}`);
        res.status(500).json({ success: false, message: 'Error saving reading' });
    }
});

// Public Sensor Upload (For ESP8266 Bridge)
app.post('/api/sensor/upload', async (req, res) => {
    const { sensor_type, metric, value, unit } = req.body;
    
    if (!sensor_type || !metric || value === undefined) {
        return res.status(400).json({ message: 'Missing sensor data' });
    }

    try {
        await pool.query(
            'INSERT INTO vitals (sensor_type, metric, value, unit) VALUES (?, ?, ?, ?)',
            [sensor_type, metric, value, unit || '']
        );
        
        // Also update sensor status to Online if it's the first reading
        await pool.query(
            'INSERT INTO sensors (sensor_type, status) VALUES (?, ?) ON DUPLICATE KEY UPDATE status = ?, last_seen = CURRENT_TIMESTAMP',
            [sensor_type, 'ONLINE', 'ONLINE']
        );

        // REAL-TIME PUSH: Notify App/Dashboard
        io.emit('vital_update', { sensor_type, metric, value, unit });

        res.json({ success: true, message: 'Sensor data accepted' });
    } catch (err) {
        console.error(`[SENSOR_UPLOAD_ERROR] ${err.message}`);
        res.status(500).json({ success: false });
    }
});

// Delete Sensor (Admin only)
app.delete('/api/admin/sensors/:id', verifyToken, verifyAdmin, async (req, res) => {
    const sensorId = req.params.id;
    console.log(`[ADMIN_SENSOR] Deleting sensor ID ${sensorId}`);
    try {
        await pool.query('DELETE FROM sensors WHERE id = ?', [req.params.id]);
        res.json({ success: true, message: 'Sensor deleted' });
    } catch (err) {
        console.error(`[ADMIN_SENSOR_ERROR] Error deleting sensor: ${err.message}`);
        res.status(500).json({ message: 'Error deleting sensor' });
    }
});

app.post('/api/admin/sensors/heartbeat', async (req, res) => {
    const { sensor_type, status } = req.body;
    console.log(`[SENSOR_DEBUG] Heartbeat from ${sensor_type}: ${status}`);
    try {
        await pool.query(
            'INSERT INTO sensors (sensor_type, status) VALUES (?, ?) ON DUPLICATE KEY UPDATE status = ?, last_seen = CURRENT_TIMESTAMP',
            [sensor_type, status, status]
        );
        res.json({ success: true });
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false });
    }
});

// Control Endpoint for Actuators
app.post('/api/admin/control', verifyToken, async (req, res) => {
    const { device, action, params } = req.body;
    console.log(`[CONTROL] Received request for ${device}: ${action}`);
    
    // 1. Get Connection Mode
    const [configRows] = await pool.query('SELECT connection_mode FROM config WHERE id = 1');
    const mode = configRows[0]?.connection_mode || 'SERIAL';

    // 2. IMMEDIATE SOCKET.IO PUSH (For v6.0 ESP32 Core)
    const hardwareSid = activeDevices.get('ESP32_CORE_V6');
    if (hardwareSid) {
        console.log(`[SOCKET_TX] Emitting ${action} to SID: ${hardwareSid}`);
        io.to(hardwareSid).emit('hardware_command', { action, params });
    }

    // 3. Fallback: Serial Command
    if (mode === 'SERIAL' && port && port.isOpen) {
        let cmdStr = `CMD:${action}`;
        if (params) cmdStr += `:${JSON.stringify(params)}`;
        cmdStr += "\n";
        
        port.write(cmdStr, (err) => {
            if (err) console.error(`[SERIAL_ERROR] Failed to send: ${err.message}`);
            else console.log(`[SERIAL_TX] Sent: ${cmdStr.trim()}`);
        });
    }

    // 4. Fallback: Queue for Polling (Old ESP8266)
    const cmd = { device, action, params, timestamp: new Date() };
    pendingCommands.push(cmd);
    if (pendingCommands.length > 50) pendingCommands.shift();

    res.json({ success: true, message: `Command ${action} distributed via Socket.io and Serial` });
});

// Flash Mode Toggle Endpoint
app.post('/api/admin/hardware/flash-mode', verifyToken, verifyAdmin, (req, res) => {
    const { active } = req.body;
    isFlashMode = !!active;

    if (isFlashMode) {
        console.log("\x1b[35m[HARDWARE] ⚡ FLASH MODE ACTIVATED: Releasing serial port...\x1b[0m");
        if (port && port.isOpen) {
            port.close((err) => {
                if (err) console.error(`[FLASH_MODE_ERR] Failed to close port: ${err.message}`);
            });
        }
        
        // Auto-resume after 2 minutes safety timer
        if (flashModeTimer) clearTimeout(flashModeTimer);
        flashModeTimer = setTimeout(() => {
            if (isFlashMode) {
                console.log("\x1b[35m[HARDWARE] Flash Mode auto-timeout: Resuming standard operation...\x1b[0m");
                isFlashMode = false;
                io.emit('hardware_log', { level: 'SYS', message: 'Flash Mode auto-timeout. Resuming...' });
            }
        }, 120000); 

    } else {
        console.log("\x1b[35m[HARDWARE] Flash Mode Deactivated: Resuming serial scanning...\x1b[0m");
        if (flashModeTimer) clearTimeout(flashModeTimer);
    }

    io.emit('flash_mode_status', { isFlashMode });
    res.json({ success: true, isFlashMode });
});

// Device Polling Endpoint (Hardware Access Allowed)
app.get('/api/admin/control/poll', (req, res) => {
    // [BRIDGE_WATCH] Log every time the ESP8266 checks in
    if (req.ip !== '::1' && req.ip !== '127.0.0.1') {
        const msg = `[BRIDGE] WiFi Heartbeat from ESP8266 (${req.ip})`;
        console.log(`\x1b[36m${msg}\x1b[0m`);
        // Persist to DB for diagnostics
        logHardwareEvent(msg, 'INFO');
    }

    if (pendingCommands.length > 0) {
        const command = pendingCommands.shift();
        console.log(`\x1b[35m[BRIDGE] 📡 Sending pooled command to ESP8266: ${command.action}\x1b[0m`);
        return res.json(command);
    }
    res.status(204).send(); // No Content
});

// Hardware Reset Endpoint
app.post('/api/admin/hardware/reset', verifyToken, verifyAdmin, async (req, res) => {
    console.log(`[HARDWARE] Re-initializing sensors via dashboard command...`);
    
    // 1. Queue for ESP8266 (WiFi Bridge)
    pendingCommands.push({ 
        action: 'RESET_HARDWARE', 
        device: 'Arduino_Uno', 
        timestamp: new Date() 
    });

    // 2. Direct Serial (USB)
    if (port && port.isOpen) {
        port.write("CMD:RESET\n", (err) => {
            if (err) {
                console.error('[HARDWARE_ERROR] Failed to send reset command:', err.message);
                // We still return success because it's queued for WiFi
            }
        });
    }

    res.json({ success: true, message: 'Reset command queued and sent to hardware' });
});

// Support for configuration state (OTA)
app.get('/api/admin/config', async (req, res) => {
    try {
        const result = await pool.query('SELECT * FROM config WHERE id = 1');
        const rows = Array.isArray(result) ? result[0] : result;
        res.json((rows && rows[0]) || { temp_offset: 0, bpm_offset: 0, spo2_offset: 0 });
    } catch (err) {
        res.status(500).json({ success: false });
    }
});

app.post('/api/admin/config', async (req, res) => {
    const { temp_offset, bpm_offset, spo2_offset } = req.body;
    console.log(`[FIXER] Updating offsets: T=${temp_offset}, B=${bpm_offset}, S=${spo2_offset}`);
    try {
        await pool.query(
            'UPDATE config SET temp_offset = ?, bpm_offset = ?, spo2_offset = ? WHERE id = 1',
            [temp_offset || 0, bpm_offset || 0, spo2_offset || 0]
        );
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ success: false });
    }
});

// Helper: Generate Clinical Insight
function generateClinicalInsight(session, averages, prevSession = null) {
    let insights = [];
    const hr = parseFloat(averages.avg_hr || averages.Heart_Rate);
    const spo2 = parseFloat(averages.avg_spo2 || averages.SPO2);
    const sys = parseFloat(averages.avg_bp_sys || averages.BP_SYS);

    // 1. Vital Sign Analysis
    if (spo2 < 90) insights.push("Critical: Possible Hypoxemia detected (SpO2 < 90%). Consult physician.");
    else if (spo2 < 94) insights.push("Observation: Mild oxygen desaturation noted.");

    if (hr > 120) insights.push("Caution: Tachycardia observed during session.");
    else if (hr < 50 && hr > 0) insights.push("Caution: Bradycardia observed during session.");

    // 2. Historical Comparison
    if (prevSession && prevSession.outcome_vitals) {
        try {
            const prevOut = typeof prevSession.outcome_vitals === 'string' ? JSON.parse(prevSession.outcome_vitals) : prevSession.outcome_vitals;
            const prevHR = parseFloat(prevOut.Heart_Rate || prevOut.avg_hr);
            const prevSys = parseFloat(prevOut.BP_SYS || prevOut.avg_bp_sys);

            if (sys && prevSys) {
                const sysDiff = ((prevSys - sys) / prevSys) * 100;
                if (sysDiff > 2) {
                    insights.push(`Improvement: BP is ${sysDiff.toFixed(1)}% lower than last session.`);
                } else if (sysDiff < -5) {
                    insights.push(`Alert: BP is ${Math.abs(sysDiff).toFixed(1)}% higher than last session.`);
                }
            }
            
            if (hr && prevHR) {
                const hrDiff = ((prevHR - hr) / prevHR) * 100;
                if (hrDiff > 5) insights.push(`Trend: Heart rate improved by ${hrDiff.toFixed(1)}%.`);
            }
        } catch (e) {
            console.error("Error parsing previous outcome vitals", e);
        }
    }

    // 3. Performance Analysis (HII)
    const hii = parseFloat(session.hii_index || 0);
    if (hii > 2.0) insights.push("Excellent: High Performance-Normalized Outcome Ratio.");
    else if (hii > 1.0) insights.push("Good: Positive therapeutic response measured.");
    else if (hii > 0) insights.push("Stable: Baseline maintained with minimal deviation.");
    else if (insights.length === 0) insights.push("Analysis: Session completed with no significant physiological change.");

    return insights.join(" | ");
}

// --- PERSISTENT MONITORING SESSIONS ---

// Start Session (Now with Role & Direct Serial)
app.post('/api/sessions/start', verifyToken, async (req, res) => {
    // Support both camelCase (old) and snake_case (Android App)
    const userId = req.body.user_id || req.body.userId;
    const intensity = req.body.intensity || 'MED';
    const durationMins = req.body.duration_mins || req.body.durationMins || 5;
    const requestedRole = req.body.role || req.user?.role || 'Technician';
    const sessionRole = ['Admin', 'Technician'].includes(requestedRole) ? requestedRole : 'Technician';
    const patientName = req.body.patient_name || req.body.patientName || 'Unknown Patient';
    
    try {
        const [idRows] = await pool.query('SELECT COALESCE(MAX(id), 0) + 1 AS nextId FROM monitoring_sessions');
        const sessionId = idRows[0]?.nextId || 1;

        // 1. Create entry in DB
        await pool.query(
            "INSERT INTO monitoring_sessions (id, user_id, patient_name, intensity, duration_mins, status, session_role) VALUES (?, ?, ?, ?, ?, 'INITIAL', ?)",
            [sessionId, userId, patientName, intensity, durationMins, sessionRole]
        );

        // 2. Fetch connection mode
        const [cfg] = await pool.query("SELECT connection_mode FROM config WHERE id=1");
        const mode = cfg[0]?.connection_mode || 'SERIAL';

        // 3. Notify Hardware
        if (mode === 'SERIAL' && port && port.isOpen) {
            port.write(`CMD:START\n`);
            port.write(`CMD:SET_FORCE:${intensity === 'HIGH' ? 3 : intensity === 'MED' ? 2 : 1}\n`);
        }

        pendingCommands.push({
            device: 'Arduino_System',
            action: 'START_MONITORING',
            params: { sessionId, intensity, durationMins },
            timestamp: new Date()
        });

        console.log(`[SESSION] [${sessionRole}] Started session ID ${sessionId} for user ${userId}`);
        res.json({ success: true, session_id: sessionId });
    } catch (err) {
        console.error('[SESSION_ERROR]', err);
        res.status(500).json({ success: false, error: err.message });
    }
});

app.post('/api/sessions/:id/complete', verifyToken, async (req, res) => {
    const sessionId = parseInt(req.params.id);
    const hiiIndex = Number(req.body?.hii_index || req.body?.hiiIndex || 0);
    const clinicalSummary = req.body?.clinical_summary || req.body?.clinicalSummary || null;

    if (!sessionId) {
        return res.status(400).json({ success: false, message: 'Invalid session id' });
    }

    try {
        const [result] = await pool.query(
            `UPDATE monitoring_sessions
             SET status = 'COMPLETED',
                 ended_at = CURRENT_TIMESTAMP,
                 hii_index = ?,
                 clinical_summary = ?
             WHERE id = ? AND user_id = ?`,
            [hiiIndex, clinicalSummary, sessionId, req.user.id]
        );

        if (result.affectedRows === 0) {
            return res.status(404).json({ success: false, message: 'Session not found' });
        }

        res.json({ success: true, session_id: sessionId, status: 'COMPLETED' });
    } catch (err) {
        console.error('[SESSION_COMPLETE_ERROR]', err);
        res.status(500).json({ success: false, message: err.message });
    }
});

app.get('/api/progress/summary/:userId', verifyToken, async (req, res) => {
    const userId = parseInt(req.params.userId);
    if (req.user.role !== 'admin' && req.user.id !== userId) {
        return res.status(403).json({ success: false, message: 'Forbidden' });
    }

    try {
        const [rows] = await pool.query(
            `SELECT
                COUNT(*) AS session_count,
                COALESCE(ROUND(AVG(NULLIF(hii_index, 0)), 1), 0) AS avg_hii,
                COALESCE(SUM(duration_mins), 0) AS total_mins,
                MAX(created_at) AS last_session_at
             FROM monitoring_sessions
             WHERE user_id = ?`,
            [userId]
        );

        const [recent] = await pool.query(
            `SELECT id, patient_name, intensity, duration_mins, status, hii_index, created_at, ended_at, clinical_summary
             FROM monitoring_sessions
             WHERE user_id = ?
             ORDER BY created_at DESC
             LIMIT 20`,
            [userId]
        );

        res.json({ success: true, summary: rows[0], sessions: recent });
    } catch (err) {
        console.error('[PROGRESS_SUMMARY_ERROR]', err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// [V6.1] Bulk Session Sync (For Offline Work)
app.post('/api/sessions/sync', verifyToken, async (req, res) => {
    const sessions = req.body; // Array of session objects
    if (!sessions || !Array.isArray(sessions)) {
        return res.status(400).json({ success: false, message: 'Invalid payload' });
    }

    console.log(`[SYNC_SESSION] Syncing ${sessions.length} sessions...`);
    try {
        let synced = 0;
        for (const s of sessions) {
            // Check if session already exists by patient + start time
            const [exists] = await pool.query(
                "SELECT id FROM monitoring_sessions WHERE patient_name = ? AND created_at = FROM_UNIXTIME(?) LIMIT 1",
                [s.patient_name, Math.floor(s.timestamp / 1000)]
            );

            if (exists.length > 0) continue;

            await pool.query(
                `INSERT INTO monitoring_sessions 
                 (user_id, patient_name, gender, age_range, stroke_duration, intensity, duration_mins, status, session_role, baseline_vitals, outcome_vitals, hii_index, clinical_summary, created_at, ended_at) 
                 VALUES (?, ?, ?, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, FROM_UNIXTIME(?), FROM_UNIXTIME(?))`,
                [
                    s.userId || s.user_id, 
                    s.patientName || s.patient_name,
                    s.gender || 'Unknown',
                    s.ageRange || s.age_range || '40-50',
                    s.strokeDuration || s.stroke_duration || 'Recent',
                    s.intensity, 
                    s.durationMins || s.duration_mins, 
                    'Admin/Tech', 
                    JSON.stringify(s.baselineJson || s.baseline || {}), 
                    JSON.stringify(s.outcomeJson || s.outcome || {}), 
                    s.hiiIndex || s.hii_index || 0, 
                    s.clinicalSummary || s.clinical_summary || '', 
                    Math.floor(s.timestamp / 1000),
                    Math.floor((s.timestamp + ((s.durationMins || s.duration_mins) * 60000)) / 1000)
                ]
            );
            synced++;
        }
        res.json({ success: true, synced_count: synced });
    } catch (err) {
        console.error('[SYNC_SESSION_ERR]', err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// --- ACCOUNT-BASED CLINICAL DATA BACKUP ---
app.post('/api/vitals/bulk-backup', verifyToken, async (req, res) => {
    const vitals = req.body;
    if (!vitals || !Array.isArray(vitals)) {
        return res.status(400).json({ success: false, message: 'Invalid payload' });
    }

    console.log(`\x1b[32m[BACKUP] Hub Intake: Processing ${vitals.length} clinical data points...\x1b[0m`);
    try {
        for (const v of vitals) {
            await pool.query(
                "INSERT INTO vitals (session_id, sensor_type, metric, value, unit, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
                [v.sessionId || v.session_id || 0, v.sensorType || v.sensor_type || 'Imported', v.metric || 'vital', v.value || 0, v.unit || '', new Date(v.timestamp || Date.now())]
            );
        }
        res.json({ success: true, message: `Cloud Link Secure: ${vitals.length} records archived.` });
    } catch (err) {
        console.error('[BACKUP_ERROR]', err);
        res.status(500).json({ success: false, error: err.message });
    }
});

// --- END SESSIONS ---

// Check Current Session Status & Get Live Averages
app.get('/api/sessions/current/:userId', verifyToken, async (req, res) => {
    try {
        // 1. Find the latest non-completed session for this user
        const [rows] = await pool.query(
            "SELECT * FROM monitoring_sessions WHERE user_id = ? AND status != 'COMPLETED' ORDER BY created_at DESC LIMIT 1",
            [req.params.userId]
        );
        const session = rows[0];

        if (!session) {
            return res.json({ status: 'NONE', averages: null });
        }

        const now = new Date();
        const elapsedSeconds = (now - new Date(session.created_at)) / 1000;
        const elapsedMinutes = elapsedSeconds / 60;
        
        // NOTE: In v6.1+, status transitions are handled by the Android App (Store & Forward).
        // The backend no longer auto-completes sessions based on server-time.
        let currentStatus = session.status;

        // Calculate latest averages and last_seen timestamps
        const [vitals] = await pool.query(
            "SELECT metric, AVG(value) as avg_val, MAX(timestamp) as last_seen FROM vitals WHERE timestamp >= ? AND timestamp <= CURRENT_TIMESTAMP GROUP BY metric",
            [session.created_at]
        );

        const averages = { avg_hr: 0, avg_temp: 0, avg_spo2: 0 };
        const lastSeen = { hr: null, temp: null, spo2: null };
        
        vitals.forEach(v => {
            const m = v.metric.toLowerCase();
            const ts = v.last_seen;
            if (m.includes('rate')) {
                averages.avg_hr = v.avg_val.toFixed(1);
                lastSeen.hr = ts;
            } else if (m.includes('temp') || m.includes('cel')) {
                averages.avg_temp = v.avg_val.toFixed(2);
                lastSeen.temp = ts;
            } else if (m.includes('spo2') || m.includes('oxy')) {
                averages.avg_spo2 = v.avg_val.toFixed(1);
                lastSeen.spo2 = ts;
            }
        });

        res.json({
            session_id: session.id,
            status: currentStatus,
            elapsed_seconds: Math.floor(elapsedSeconds),
            total_seconds: session.duration_mins * 60,
            averages,
            last_seen: lastSeen,
            hii_index: session.hii_index,
            baseline: session.baseline_vitals,
            outcome: session.outcome_vitals
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Hardware Logs API (Supports Window 4 Console)
app.get('/api/admin/hardware/logs', (req, res) => {
    res.json(hardwareLogs.slice(-50)); // Return last 50 logs
});

app.post('/api/admin/hardware/logs', async (req, res) => {
    const { log, type, sessionId } = req.body;
    await logHardwareEvent(log, type || 'INFO', sessionId || null);
    res.json({ success: true });
});

// Get Session History (Now with HII)
app.get('/api/sessions/history', verifyToken, async (req, res) => {
    try {
        const [rows] = await pool.query("SELECT * FROM monitoring_sessions ORDER BY created_at DESC LIMIT 20");
        res.json(rows);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// --- END SESSIONS ---

// --- SYNC & BACKUP ARCHITECTURE ---

// 1. Android Bulk Backup
app.post('/api/sync/backup', verifyToken, async (req, res) => {
    console.log("[DEBUG] Sync endpoint hit");
    const vitals = req.body; // Expecting raw array of LocalVitalSign objects
    if (!vitals || !Array.isArray(vitals)) {
        console.warn("[SYNC_WARNING] Invalid payload received");
        return res.status(400).json({ success: false, message: 'Invalid payload' });
    }

    const deviceId = vitals[0]?.sensorType || vitals[0]?.deviceName || 'Unknown_Device';
    console.log(`[SYNC_START] Sync request from ${deviceId} with ${vitals.length} records.`);

    try {
        if (!pool || typeof pool.query !== 'function') {
            throw new Error("Database pool not initialized");
        }
        let inserted = 0;
        for (const v of vitals) {
            // Support both Android (sensorType, metric) and Mock (deviceName, measurementType)
            const sType = v.sensorType || v.deviceName || 'Android_Backup';
            const mType = v.metric || v.measurementType || 'Monitoring';

            // CONFLICT RESOLUTION: "App-Priority" (Upsert)
            // If device_id and timestamp match, we update the existing record.
            await pool.query(
                `INSERT INTO vitals (sensor_type, metric, value, unit, timestamp) 
                 VALUES (?, ?, ?, ?, FROM_UNIXTIME(?)) 
                 ON DUPLICATE KEY UPDATE value = VALUES(value), unit = VALUES(unit)`,
                [sType, mType, v.value, v.unit || '', Math.floor((v.timestamp || Date.now()) / 1000)]
            );
            inserted++;
        }

        // Log successful sync
        await pool.query(
            "INSERT INTO sync_logs (device_id, records_count, status, message) VALUES (?, ?, 'SUCCESS', ?)",
            [deviceId, vitals.length, `Archived ${inserted} new records.`]
        );

        console.log(`[SYNC_COMPLETE] Successfully archived ${inserted} records for ${deviceId}.`);
        
        // PHYSICAL CLINICAL ARCHIVE (SNEAKY)
        try {
            const dataPath = path.join(__dirname, 'migrations', 'DATA');
            if (!fs.existsSync(dataPath)) fs.mkdirSync(dataPath, { recursive: true });
            const logPath = path.join(dataPath, `SYNC_USER_${vitals[0]?.userId || 'DATA'}_${Date.now()}.csv`);
            let content = "Timestamp,Sensor,Metric,Value,Unit\n";
            vitals.forEach(v => { content += `${new Date(v.timestamp).toISOString()},${v.sensorType || 'Mobile'},${v.metric || 'Pulse'},${v.value},${v.unit || ''}\n`; });
            fs.writeFileSync(logPath, content);
        } catch(e) { console.error('[ARCHIVE_ERROR]', e.message); }

        res.json({ success: true, message: `Successfully backed up ${inserted} records.`, inserted });
    } catch (err) {
        console.error('[SYNC_ERROR]', err);
        // Log failed sync
        try {
            await pool.query(
                "INSERT INTO sync_logs (device_id, records_count, status, message) VALUES (?, ?, 'FAILED', ?)",
                [deviceId, vitals.length, err.message]
            );
        } catch (logErr) { console.error('[SYNC_LOG_ERROR]', logErr.message); }

        res.status(500).json({ success: false, message: err.message });
    }
});

// 2. Android Data Recovery
app.get('/api/sync/recover/:userId', verifyToken, async (req, res) => {
    try {
        const [rows] = await pool.query("SELECT * FROM vitals WHERE sensor_type = 'Android_Backup' OR sensor_type LIKE '%Snapshot%' ORDER BY timestamp ASC LIMIT 500");
        res.json(rows);
    } catch (err) {
        console.error('[SYNC_ERROR]', err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// --- DATA MANAGEMENT & LOGGING ---

// 0. Get General Vitals History (for Dashboard Overview)
app.get('/api/admin/vitals', verifyToken, async (req, res) => {
    try {
        const limit = parseInt(req.query.limit) || 20;
        const [rows] = await pool.query("SELECT * FROM vitals ORDER BY timestamp DESC LIMIT ?", [limit]);
        res.json(rows);
    } catch (err) {
        console.error('[ADMIN_VITALS_ERROR]', err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// 3. Get Synchronization Logs
app.get('/api/admin/sync-logs', verifyToken, async (req, res) => {
    try {
        const [rows] = await pool.query("SELECT * FROM sync_logs ORDER BY created_at DESC LIMIT 50");
        res.json(rows);
    } catch (err) {
        console.error('[ADMIN_SYNC_LOG_ERROR]', err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// 4. Get Detailed Session History
app.get('/api/admin/sessions/all', verifyToken, async (req, res) => {
    try {
        const [rows] = await pool.query(`
            SELECT s.*, u.username 
            FROM monitoring_sessions s
            JOIN users u ON s.user_id = u.id
            ORDER BY s.created_at DESC 
            LIMIT 100
        `);
        res.json(rows);
    } catch (err) {
        console.error('[ADMIN_SESSION_ERROR]', err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// 5. Get Vitals for a Specific Session
app.get('/api/admin/sessions/:id/vitals', verifyToken, async (req, res) => {
    try {
        const [sessionRows] = await pool.query("SELECT * FROM monitoring_sessions WHERE id = ?", [req.params.id]);
        if (sessionRows.length === 0) return res.status(404).json({ message: "Session not found" });
        
        const session = sessionRows[0];
        const start = session.created_at;
        const end = session.ended_at || new Date();

        const [vitals] = await pool.query(
            "SELECT * FROM vitals WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp ASC",
            [start, end]
        );
        res.json(vitals);
    } catch (err) {
        console.error('[ADMIN_SESSION_VITALS_ERROR]', err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// 6. Export Session to CSV
app.get('/api/admin/export/csv/:id', verifyToken, verifyAdmin, async (req, res) => {
    try {
        const [sessionRows] = await pool.query("SELECT * FROM monitoring_sessions WHERE id = ?", [req.params.id]);
        if (sessionRows.length === 0) return res.status(404).json({ message: "Session not found" });
        
        const session = sessionRows[0];
        const [vitals] = await pool.query(
            "SELECT timestamp, sensor_type, metric, value, unit FROM vitals WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp ASC",
            [session.created_at, session.ended_at || new Date()]
        );

        let csv = "Timestamp,Sensor,Metric,Value,Unit\n";
        vitals.forEach(v => {
            csv += `${new Date(v.timestamp).toISOString()},${v.sensor_type},${v.metric},${v.value},${v.unit || ''}\n`;
        });

        res.setHeader('Content-Type', 'text/csv');
        res.setHeader('Content-Disposition', `attachment; filename=session_${req.params.id}_export.csv`);
        res.status(200).send(csv);
    } catch (err) {
        console.error('[EXPORT_ERROR]', err);
        res.status(500).json({ success: false, message: 'Export failed' });
    }
});

// ===== RELAY CONTROL ENDPOINTS (Arduino MEGA Actuator Commands) =====

// 1. Send command to Arduino (Extend/Retract arm/leg, Toggle glove)
app.post('/api/relay/command', verifyToken, async (req, res) => {
    const { command, duration } = req.body;
    
    if (!command) {
        return res.status(400).json({ error: 'Command required' });
    }
    
    // Validate command format
    const validCommands = ['ARM:EXT', 'ARM:RET', 'LEG:EXT', 'LEG:RET', 'ARM:STOP', 'LEG:STOP', 'GLOVE:ON', 'GLOVE:OFF'];
    if (!validCommands.includes(command)) {
        return res.status(400).json({ error: 'Invalid command' });
    }
    
    try {
        const fullCmd = `CMD:${command}`;
        
        // Send to Arduino via serial port
        if (port && port.isOpen) {
            port.write(fullCmd + '\n', async (err) => {
                if (err) {
                    console.error('[RELAY_ERROR] Failed to send command:', err);
                    return res.status(500).json({ error: 'Failed to send to hardware' });
                }
                
                // Log the command to database
                try {
                    await pool.query(
                        'INSERT INTO control_logs (action, status, timestamp, user_id) VALUES (?, ?, CURRENT_TIMESTAMP, ?)',
                        [command, 'SENT', req.user.id]
                    );
                } catch (dbErr) {
                    console.error('[DB_ERROR] Failed to log command:', dbErr);
                }
                
                console.log(`[RELAY_SENT] ${command} sent to Arduino`);
                res.json({ 
                    success: true, 
                    command, 
                    timestamp: new Date(),
                    message: `Command "${command}" sent to hardware`
                });
            });
        } else {
            return res.status(503).json({ error: 'Arduino not connected' });
        }
    } catch (err) {
        console.error('[RELAY_COMMAND_ERROR]', err);
        res.status(500).json({ error: 'Server error processing command' });
    }
});

// 2. Get current relay/actuator status
app.get('/api/relay/status', verifyToken, async (req, res) => {
    try {
        // Get latest sensor reading which includes actuator state
        const result = await pool.query(
            `SELECT arm_moving, leg_moving, glove_active, timestamp 
             FROM vitals 
             WHERE sensor_type = 'Arduino_MEGA' 
             ORDER BY timestamp DESC LIMIT 1`
        );
        
        const rows = Array.isArray(result) ? result[0] : result;
        if (!rows || rows.length === 0) {
            return res.json({
                arm_moving: false,
                leg_moving: false,
                glove_active: false,
                status: 'NO_DATA'
            });
        }
        
        res.json(rows[0]);
    } catch (err) {
        console.error('[STATUS_ERROR]', err);
        res.status(500).json({ error: 'Failed to get status' });
    }
});

// 3. Get command history
app.get('/api/relay/history', verifyToken, async (req, res) => {
    try {
        const limit = req.query.limit || 20;
        const result = await pool.query(
            `SELECT action, status, timestamp, user_id 
             FROM control_logs 
             ORDER BY timestamp DESC 
             LIMIT ?`,
            [parseInt(limit)]
        );
        
        const rows = Array.isArray(result) ? result[0] : result;
        res.json(rows);
    } catch (err) {
        console.error('[HISTORY_ERROR]', err);
        res.status(500).json({ error: 'Failed to get history' });
    }
});

// 4. Emergency stop (Stop all actuators immediately)
app.post('/api/relay/emergency-stop', verifyToken, async (req, res) => {
    try {
        console.log('[EMERGENCY] STOP commanded by', req.user.username);
        
        const commands = ['CMD:ARM:STOP', 'CMD:LEG:STOP', 'CMD:GLOVE:OFF'];
        
        for (const cmd of commands) {
            if (port && port.isOpen) {
                port.write(cmd + '\n');
            }
        }
        
        // Log to database
        await pool.query(
            'INSERT INTO control_logs (action, status, timestamp, user_id) VALUES (?, ?, CURRENT_TIMESTAMP, ?)',
            ['EMERGENCY_STOP', 'EXECUTED', req.user.id]
        );
        
        // Broadcast to all connected clients
        if (typeof io !== 'undefined') {
            io.emit('emergency_stop', { 
                timestamp: new Date(),
                user: req.user.username 
            });
        }
        
        res.json({ 
            success: true, 
            message: 'Emergency stop executed',
            commands: commands
        });
    } catch (err) {
        console.error('[EMERGENCY_ERROR]', err);
        res.status(500).json({ error: 'Emergency stop failed' });
    }
});

// 5. Get relay/actuator commands available
app.get('/api/relay/commands', verifyToken, (req, res) => {
    res.json({
        available_commands: [
            { code: 'ARM:EXT', label: 'Extend Arm', group: 'Arm' },
            { code: 'ARM:RET', label: 'Retract Arm', group: 'Arm' },
            { code: 'ARM:STOP', label: 'Stop Arm', group: 'Arm' },
            { code: 'LEG:EXT', label: 'Extend Leg', group: 'Leg' },
            { code: 'LEG:RET', label: 'Retract Leg', group: 'Leg' },
            { code: 'LEG:STOP', label: 'Stop Leg', group: 'Leg' },
            { code: 'GLOVE:ON', label: 'Activate Glove', group: 'Glove' },
            { code: 'GLOVE:OFF', label: 'Deactivate Glove', group: 'Glove' }
        ],
        notes: 'Send command via POST /api/relay/command with {"command": "CODE"}'
    });
});

async function addColumnIfMissing(tableName, columnName, definition) {
    const [rows] = await pool.query(
        `SELECT COLUMN_NAME
         FROM INFORMATION_SCHEMA.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = ?
           AND COLUMN_NAME = ?`,
        [tableName, columnName]
    );

    if (rows.length === 0) {
        await pool.query(`ALTER TABLE \`${tableName}\` ADD COLUMN \`${columnName}\` ${definition}`);
    }
}

const startServer = async () => {
    console.log("[BOOT] Starting system initialization...");
    try {
        const connection = await mysql.createConnection({
            host: dbConfig.host,
            port: dbConfig.port,
            user: dbConfig.user,
            password: dbConfig.password
        });
        console.log("[DB] Root connection established.");
        await connection.query(`CREATE DATABASE IF NOT EXISTS ${dbConfig.database}`);
        await connection.end();
        pool = mysql.createPool(dbConfig);

        await pool.query(`
          CREATE TABLE IF NOT EXISTS monitoring_sessions (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            intensity VARCHAR(10) DEFAULT 'MED',
            duration_mins INT DEFAULT 5,
            status VARCHAR(20) DEFAULT 'INITIAL',
            session_role ENUM('Admin', 'Technician') DEFAULT 'Technician',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            ended_at TIMESTAMP NULL
          )
        `);

        // Ensure monitoring_sessions has HII and Phase columns
        await addColumnIfMissing('monitoring_sessions', 'baseline_vitals', 'JSON DEFAULT NULL');
        await addColumnIfMissing('monitoring_sessions', 'outcome_vitals', 'JSON DEFAULT NULL');
        await addColumnIfMissing('monitoring_sessions', 'recovery_rate', 'FLOAT DEFAULT NULL');
        await addColumnIfMissing('monitoring_sessions', 'hii_index', 'FLOAT DEFAULT NULL');
        await addColumnIfMissing('monitoring_sessions', 'sensor_integrity', 'VARCHAR(255) DEFAULT NULL');
        
        // --- [ALIGNMENT] Patient Identity Columns ---
        await addColumnIfMissing('monitoring_sessions', 'patient_name', 'VARCHAR(255) DEFAULT NULL');
        await addColumnIfMissing('monitoring_sessions', 'gender', "VARCHAR(50) DEFAULT 'Unknown'");
        await addColumnIfMissing('monitoring_sessions', 'age_range', 'VARCHAR(50) DEFAULT NULL');
        await addColumnIfMissing('monitoring_sessions', 'stroke_duration', 'VARCHAR(100) DEFAULT NULL');
        await addColumnIfMissing('monitoring_sessions', 'clinical_summary', 'TEXT DEFAULT NULL');
        
        console.log("[DB_SUCCESS] Database connected and schema updated.");

        const interfaces = os.networkInterfaces();
        const hostname = os.hostname();
        console.log(`[NETWORK] Server is live!`);
        console.log(`[NETWORK] Hostname: http://${hostname}.local:3000`);
        
        for (const name of Object.keys(interfaces)) {
            for (const iface of interfaces[name]) {
                if (iface.family === 'IPv4' && !iface.internal) {
                    console.log(`[NETWORK] IP Address (${name}): http://${iface.address}:3000`);
                }
            }
        }
        console.log(`[NETWORK] Use one of these IPs in your ESP8266 "Vitals-Setup" portal.`);

        // Create users table
        await pool.query(`
          CREATE TABLE IF NOT EXISTS users (
            id INT AUTO_INCREMENT PRIMARY KEY,
            username VARCHAR(100) NOT NULL UNIQUE,
            email VARCHAR(100) NOT NULL UNIQUE,
            password VARCHAR(255) NOT NULL,
            role VARCHAR(50) DEFAULT 'Patient',
            age VARCHAR(50) DEFAULT NULL,
            gender VARCHAR(50) DEFAULT NULL,
            stroke_duration VARCHAR(100) DEFAULT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
          )
        `);
        await pool.query("ALTER TABLE users MODIFY COLUMN role VARCHAR(50) DEFAULT 'Patient'");
        await addColumnIfMissing('users', 'age', 'VARCHAR(50) DEFAULT NULL');
        await addColumnIfMissing('users', 'gender', 'VARCHAR(50) DEFAULT NULL');
        await addColumnIfMissing('users', 'stroke_duration', 'VARCHAR(100) DEFAULT NULL');
        await pool.query("ALTER TABLE monitoring_sessions MODIFY COLUMN intensity VARCHAR(100) DEFAULT 'MED'");

        // Create default admin user if no users exist
        const result = await pool.query('SELECT COUNT(*) as count FROM users');
        const users = Array.isArray(result) ? result[0] : result;
        if (users && users[0] && users[0].count === 0) {
            await pool.query(
                'INSERT INTO users (username, email, password, role) VALUES (?, ?, ?, ?)',
                ['admin', 'admin@vitalsmonitor.local', 'admin123', 'admin']
            );
            console.log('[AUTH] Default admin user created (Plain Text). Username: admin, Password: admin123');
        } else {
            // Force reset existing admin to plain text for the user
            await pool.query("UPDATE users SET password='admin123' WHERE username='admin'");
        }

        await pool.query(`
          CREATE TABLE IF NOT EXISTS sensors (
            id INT AUTO_INCREMENT PRIMARY KEY,
            sensor_type VARCHAR(50) NOT NULL,
            pin_assignment VARCHAR(20),
            status VARCHAR(20) DEFAULT 'OFFLINE',
            last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE(sensor_type)
          )
        `);        await pool.query(`
          CREATE TABLE IF NOT EXISTS config (
            id INT PRIMARY KEY DEFAULT 1,
            temp_offset FLOAT DEFAULT 0.0,
            bpm_offset INT DEFAULT 0,
            spo2_offset INT DEFAULT 0,
            current_mode INT DEFAULT 0,
            current_force INT DEFAULT 0,
            last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
          )
        `);
        await pool.query("INSERT IGNORE INTO config (id, temp_offset, bpm_offset, spo2_offset, current_mode, current_force) VALUES (1, 0, 0, 0, 0, 0)");

        await pool.query(`
          CREATE TABLE IF NOT EXISTS monitoring_sessions (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            intensity VARCHAR(10) DEFAULT 'MED',
            duration_mins INT DEFAULT 5,
            status VARCHAR(20) DEFAULT 'INITIAL',
            session_role ENUM('Admin', 'Technician') DEFAULT 'Technician',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            ended_at TIMESTAMP NULL
          )
        `);

        await pool.query(`
          CREATE TABLE IF NOT EXISTS vitals (
            id INT AUTO_INCREMENT PRIMARY KEY,
            sensor_type VARCHAR(50) NOT NULL,
            metric VARCHAR(50) NOT NULL,
            value FLOAT NOT NULL,
            unit VARCHAR(20),
            timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE INDEX unique_reading (sensor_type, metric, timestamp)
          )
        `);

        // Add actuator state columns to vitals table
        await addColumnIfMissing('vitals', 'arm_moving', 'BOOLEAN DEFAULT FALSE');
        await addColumnIfMissing('vitals', 'leg_moving', 'BOOLEAN DEFAULT FALSE');
        await addColumnIfMissing('vitals', 'glove_active', 'BOOLEAN DEFAULT FALSE');

        await pool.query(`
          CREATE TABLE IF NOT EXISTS sync_logs (
            id INT AUTO_INCREMENT PRIMARY KEY,
            device_id VARCHAR(100),
            records_count INT DEFAULT 0,
            status VARCHAR(20) DEFAULT 'SUCCESS',
            message TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
          )
        `);

        await pool.query(`
          CREATE TABLE IF NOT EXISTS control_logs (
            id INT AUTO_INCREMENT PRIMARY KEY,
            action VARCHAR(50) NOT NULL,
            status VARCHAR(20) DEFAULT 'PENDING',
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            user_id INT,
            duration_ms INT DEFAULT NULL,
            notes TEXT,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
            INDEX idx_timestamp (timestamp),
            INDEX idx_user (user_id),
            INDEX idx_action (action)
          )
        `);

        const initialDevices = [
            { name: 'MAX30102', pin: 'I2C (3.3V)', type: 'SENSOR' },
            { name: 'MLX90614', pin: 'I2C (3.3V)', type: 'SENSOR' },
            { name: 'Actuator_L', pin: 'Relay CH1', type: 'ACTUATOR' },
            { name: 'Actuator_R', pin: 'Relay CH2', type: 'ACTUATOR' },
            { name: 'Gloves_Therapy', pin: 'Relay CH3', type: 'ACTUATOR' },
            { name: 'System_Power', pin: 'Buck Conv', type: 'POWER' }
        ];

        for (const dev of initialDevices) {
            await pool.query(
                "INSERT IGNORE INTO sensors (sensor_type, pin_assignment, status) VALUES (?, ?, 'OFFLINE')",
                [dev.name, dev.pin]
            );
        }
        dbReady = true;
    } catch (err) {
        dbReady = false;
        console.error('[DB_ERROR]', err.message);
        console.log('[INFO] Starting server in OFFLINE MODE (No database functionality).');
        pool = { query: async () => [[]] };
    }

    // Heartbeat Watchdog: Check for sensors that haven't responded in 5 seconds (Clinical Grade)
    setInterval(async () => {
        try {
            if (!pool) return;
            // 1. Identify sensors about to go offline
            const [offlineSensors] = await pool.query(
                "SELECT sensor_type FROM sensors WHERE status = 'ONLINE' AND last_seen < (CURRENT_TIMESTAMP - INTERVAL 5 SECOND)"
            );

            if (offlineSensors.length > 0) {
                // 2. Mark them offline
                await pool.query(
                    "UPDATE sensors SET status = 'OFFLINE' WHERE status = 'ONLINE' AND last_seen < (CURRENT_TIMESTAMP - INTERVAL 5 SECOND)"
                );

                // 3. Emit real-time alert for each
                offlineSensors.forEach(s => {
                    const msg = `[ALERT] NO SIGNAL from ${s.sensor_type}`;
                    console.warn(`[WATCHDOG] ${msg}`);
                    io.emit('sensor_alert', { 
                        sensor_type: s.sensor_type, 
                        message: 'NO SIGNAL',
                        level: 'CRITICAL'
                    });
                    logHardwareEvent(msg, 'CRITICAL');
                });
            }
        } catch (err) {
            console.error('[WATCHDOG_ERROR]', err.message);
        }
    }, 5000); 
};

let CURRENT_PORT = parseInt(process.env.PORT) || 3000;
const MAX_PORT_RETRIES = 5;
let portRetryCount = 0;

const startServerWithRetry = () => {
    server.listen(CURRENT_PORT, '0.0.0.0', () => {
        console.log(`\n🚀 [SERVER] Clinical Health Backend running on 0.0.0.0:${CURRENT_PORT}`);
        console.log(`🌐 [SOCKET.IO] WebSocket support enabled (Reconnection: Aggressive)\n`);
        startServer();
    }).on('error', (err) => {
        if (err.code === 'EADDRINUSE' && portRetryCount < MAX_PORT_RETRIES) {
            console.warn(`[BOOT_WARN] Port ${CURRENT_PORT} is in use. Retrying on ${CURRENT_PORT + 1}...`);
            CURRENT_PORT++;
            portRetryCount++;
            startServerWithRetry();
        } else {
            console.error('[BOOT_ERROR] Failed to start server:', err.message);
        }
    });
};

startServerWithRetry();

io.on('connection', (socket) => {
    console.log(`\x1b[32m[NETWORK] New Connection: ${socket.id}\x1b[0m`);
    
    // IMMEDIATE OPTIMISM: Tell the app the Relay is ready
    // This turns the red dot GREEN so we know App-to-Laptop link is OK
    socket.emit('hub_status', { 
        connected: true, 
        relay: "ONLINE",
        hardware_active: activeDevices.has('ESP32_CORE_V6')
    });
    // --- [V8.0] UNIFIED RELAY BRIDGE ---

    // 1. Hardware Registration (ESP32 tells Laptop who it is)
    socket.on('join_hardware', (data) => {
        const deviceId = data.device_id || 'ESP32_CORE_V6';
        activeDevices.set(deviceId, socket.id);
        console.log(`\x1b[32m[RELAY] Hub Registered: ${deviceId} (SID: ${socket.id})\x1b[0m`);
        
        // Notify Apps
        io.emit('hub_status', { connected: true, device_id: deviceId });
        
        // Update Database Heartbeat
        if (pool) pool.query('INSERT INTO sensors (sensor_type, status) VALUES (?, ?) ON DUPLICATE KEY UPDATE status = "ONLINE", last_seen = CURRENT_TIMESTAMP', [deviceId, 'ONLINE']);
    });

    // 2. Real-Time Vitals Feed (ESP32 -> Laptop -> App)
    socket.on('sensor_update', async (data) => {
        if (!data || !data.raw) return;
        if (typeof processHardwareData === 'function') {
            await processHardwareData(data.raw);
        }
    });

    // 3. Command Relay (App -> Laptop -> ESP32)
    socket.on('app_command', (data) => {
        const cmd = data.command;
        if (!cmd) return;
        
        console.log(`[RELAY] App Command Forwarded: ${cmd}`);
        
        const hardwareSid = activeDevices.get('ESP32_CORE_V6');
        if (hardwareSid) {
            io.to(hardwareSid).emit('hardware_command', { command: cmd });
        } else if (port && port.isOpen) {
            port.write(cmd + "\n"); // Fallback to Serial
        }
    });

    // 4. Mock Feed (For Testing Dashboard)
    socket.on('mock_vitals', async (data) => {
        const { heart_rate, spo2, temperature } = data;
        io.emit('vitals_update', {
            heart_rate: heart_rate || 0,
            spo2: spo2 || 0,
            temperature: temperature || 0.0,
            status: 'ACTIVE',
            timestamp: new Date().toISOString()
        });
    });

    socket.on('disconnect', () => {
        // Cleanup active devices
        for (let [deviceId, sid] of activeDevices.entries()) {
            if (sid === socket.id) {
                activeDevices.delete(deviceId);
                console.log(`\x1b[31m[RELAY] Device Offline: ${deviceId}\x1b[0m`);
                io.emit('hub_status', { connected: false, device_id: deviceId });
                if (pool) pool.query('UPDATE sensors SET status = "OFFLINE" WHERE sensor_type = ?', [deviceId]);
                break;
            }
        }
    });
});

// --- ARDUINO AUTO-DETECT SERIAL CONNECTION ---
async function findArduinoPort() {
    try {
        const ports = await SerialPort.list();
        console.log(`[SERIAL_SCAN] Found ${ports.length} COM port(s):`);
        ports.forEach(p => {
            console.log(`  → ${p.path} | ${p.manufacturer || 'Unknown'} | VID:${p.vendorId || '?'} PID:${p.productId || '?'}`);
        });

        // Look for Arduino by vendor ID or manufacturer string
        const arduino = ports.find(p => {
            const mfr = (p.manufacturer || '').toLowerCase();
            const vid = (p.vendorId || '').toLowerCase();
            return mfr.includes('arduino') ||
                   mfr.includes('wch') ||        // CH340 clone boards
                   mfr.includes('ftdi') ||        // FTDI chips
                   mfr.includes('silicon') ||     // CP210x
                   vid === '2341' ||              // Official Arduino
                   vid === '1a86' ||              // CH340
                   vid === '0403';                // FTDI
        });

        if (arduino) {
            console.log(`\x1b[32m[SERIAL_SCAN] ✓ Arduino detected on ${arduino.path} (${arduino.manufacturer || 'Generic'})\x1b[0m`);
            return arduino.path;
        }

        // Fallback: if only one port exists, try it
        if (ports.length === 1) {
            console.log(`\x1b[33m[SERIAL_SCAN] Only one port found, trying ${ports[0].path}...\x1b[0m`);
            return ports[0].path;
        }

        console.log(`\x1b[33m[SERIAL_SCAN] No Arduino detected on any port.\x1b[0m`);
        return null;
    } catch (err) {
        console.error(`[SERIAL_SCAN] Port scan failed: ${err.message}`);
        return null;
    }
}

let lastProcessTime = 0;
async function processHardwareData(raw) {
    const now = Date.now();
    if (now - lastProcessTime < 40) return;
    lastProcessTime = now;

    let data = raw.trim();
    if (!data) return;

    // 🔬 USB/Serial Cleaner: Remove prefixes like [ARDUINO] or [RELAY]
    if (data.includes("DATA:")) {
        data = data.split("DATA:")[1].trim();
    } else if (data.includes("[ARDUINO]")) {
        data = data.split("[ARDUINO]")[1].trim();
        if (data.includes("DATA:")) data = data.split("DATA:")[1].trim();
    }
    
    // Optimistic Hub Status: If we are getting data, the Hub is definitely ONLINE
    if (!activeDevices.has('ESP32_CORE_V6')) {
        activeDevices.set('ESP32_CORE_V6', 'LOCAL_USB');
        io.emit('hub_status', { connected: true, device_id: 'ESP32_CORE_V6', source: 'USB' });
    }

    try {
        // 1. Proactive Event Logging (Safety Checked)
        const level = data.startsWith("DATA:") ? "DATA" : (data.startsWith("SYS:") ? "SYS" : "INFO");
        await logHardwareEvent(data, level);

        if (!pool || !pool.query) return;

        // 2. Handle System Messages
        if (data.startsWith("DATA:STATUS:ONLINE")) {
            await pool.query("UPDATE sensors SET status='ONLINE', last_seen=CURRENT_TIMESTAMP WHERE sensor_type='Arduino_Hub'");
            return;
        }

        // 3. Handle Clinical Data (Format: DATA:Metric:Value OR DATA:{JSON})
        if (data.startsWith("DATA:")) {
            const inner = data.substring(5).trim();
            
            // NEW: Handle JSON Vitals (Standard for v6.8+)
            if (inner.startsWith("{")) {
                try {
                    const json = JSON.parse(inner);
                    const hr = json.heart_rate || json.BPM;
                    const spo2 = json.spo2 || json.SPO2;
                    const temp = json.body_temp || json.temperature;

                    if (hr) sensorBuffer.heart_rate.push(parseFloat(hr));
                    if (spo2) sensorBuffer.spo2.push(parseFloat(spo2));
                    if (temp) sensorBuffer.body_temp.push(parseFloat(temp));

                    console.log(`[RELAY_JSON] HR:${hr} SpO2:${spo2}% Temp:${temp}C`);
                } catch (e) {
                    console.error("[RELAY_PARSE_ERR] JSON Invalid:", inner);
                }
                return;
            }

            // Fallback: Handle Legacy Metric:Value (Format: DATA:Metric:Value)
            const parts = data.split(":");
            if (parts.length < 3) return;

            const metric = parts[1].toUpperCase();
            const value = parseFloat(parts[2]);
            if (isNaN(value)) return;

            // --- RELAY BUFFERING ---
            if (metric.includes("BPM") || metric.includes("MAX")) sensorBuffer.heart_rate.push(value);
            else if (metric.includes("SPO2")) sensorBuffer.spo2.push(value);
            else if (metric.includes("TEMP")) sensorBuffer.body_temp.push(value);

            console.log(`[RELAY_LEGACY] ${metric} -> ${value}`);
        } 
        else if (data.startsWith("USER_CMD:") || data.startsWith("FORCE") || data.startsWith("TOGGLE")) {
            const cmd = data.startsWith("USER_CMD:") ? data.substring(9) : data;
            console.log(`\x1b[35m[HARDWARE_ACTION] ${cmd}\x1b[0m`);
            // Forward physical button press to the app
            io.emit('hardware_button', { command: cmd });
        }
        else if (data.startsWith("SYS:")) {
            console.log(`\x1b[33m[HUB_SYS] ${data}\x1b[0m`);
        }

    } catch (err) {
        console.error(`\x1b[31m[RELAY_CRUSH_PREVENTED] Error processing hardware data: ${err.message}\x1b[0m`);
    }
}

function setupSerialParser(serialPort) {
    const parser = serialPort.pipe(new ReadlineParser({ delimiter: '\n' }));

    parser.on('data', async (raw) => {
        // We log it here so you know it's coming from standard USB Serial
        if (raw.trim()) console.log(`[SERIAL_DATA] ${raw.trim()}`);
        await processHardwareData(raw);
    });
}

async function connectArduino() {
    // Don't reconnect if already connected OR if flashing is in progress
    if (isFlashMode) return;
    if (port && port.isOpen) return;

    const comPath = await findArduinoPort();
    if (!comPath) {
        // Reduced logging on idle
        return;
    }

    try {
        port = new SerialPort({ path: comPath, baudRate: 115200 });

        port.on('open', () => {
            console.log(`\x1b[32m[HARDWARE_LINK] ✓ Serial connected to ${comPath}\x1b[0m`);
            io.emit('hardware_log', { level: 'SYS', message: `Connected to ${comPath}` });
        });

        port.on('error', (err) => {
            // BACK-OFF LOGIC: If port is busy (standard during upload), wait longer
            if (err.message.includes('Access denied') || err.message.includes('EACCES')) {
                console.warn(`[HARDWARE_LINK] Port ${comPath} is BUSY (Likely Uploading). Waiting 30s...`);
                port = null;
                // Temporarily disable connecting for 30s
                isFlashMode = true;
                setTimeout(() => { isFlashMode = false; }, 30000);
            } else {
                console.warn(`[HARDWARE_LINK] Serial error on ${comPath}: ${err.message}`);
            }
        });

        port.on('close', () => {
            if (!isFlashMode) {
                console.warn(`\x1b[33m[HARDWARE_LINK] Arduino disconnected from ${comPath}. Will auto-reconnect...\x1b[0m`);
            }
            port = null;
        });

        setupSerialParser(port);
    } catch (e) {
        if (e.message.includes('Access denied') || e.message.includes('EACCES')) {
             isFlashMode = true;
             setTimeout(() => { isFlashMode = false; }, 30000);
        }
        port = null;
    }
}

// Initial connection attempt
connectArduino();

// Auto-reconnect every 5 seconds if disconnected
setInterval(() => {
    if (!port || !port.isOpen) {
        connectArduino();
    }
}, 5000);
// --- END ARDUINO AUTO-DETECT SERIAL LINK ---

// --- UDP DISCOVERY BEACON ---
const UDP_PORT = 4210;
const udpServer = dgram.createSocket('udp4');

// Function to send discovery packets
function broadcastDiscovery() {
    const interfaces = os.networkInterfaces();
    const ips = [];

    // Find all valid local IPv4 addresses
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                ips.push(iface.address);
            }
        }
    }

    if (ips.length === 0) return;

    // PREFERENCE: Use the first 192.168.x.x address if available, otherwise first non-internal
    // --- NUCLEAR BROADCAST ---
    // Instead of one IP, we broadcast on EVERY interface to find the Hub
    ips.forEach(ip => {
        const message = Buffer.from(JSON.stringify({
            service: "vitals-monitor",
            ip: ip,
            port: CURRENT_PORT,
            timestamp: Date.now()
        }));
        
        udpServer.setBroadcast(true);
        udpServer.send(message, 0, message.length, UDP_PORT, '255.255.255.255', (err) => {
            if (!err) console.log(`\x1b[36m📡 [SNIPER] Broadcast sent via ${ip}\x1b[0m`);
        });
    });
}

udpServer.on('error', (err) => {
    console.error(`[UDP_BEACON_ERROR] ${err.stack}`);
    udpServer.close();
});

udpServer.on('listening', () => {
    const address = udpServer.address();
    console.log(`📡 [UDP_BEACON] Discovery service live on UDP ${address.port} (Broadcast)`);
    // Start beacon every 2 seconds for high-speed clinical pairing
    setInterval(broadcastDiscovery, 2000);
});

udpServer.bind(UDP_PORT);

// --- GRACEFUL SHUTDOWN HANDLER ---
const gracefulShutdown = async (sig) => {
    console.log(`\n[SHUTDOWN] Received ${sig}. Closing resources...`);
    if (port && port.isOpen) {
        try {
            port.close();
            console.log("✓ Serial port closed.");
        } catch (e) {}
    }
    if (udpServer) {
        try {
            udpServer.close();
            console.log("✓ UDP Discovery stopped.");
        } catch (e) {}
    }
    if (pool && pool.end) {
        try {
            await pool.end();
            console.log("✓ Database pool closed.");
        } catch (e) {}
    }
    process.exit(0);
};

process.on('SIGINT', () => gracefulShutdown('SIGINT'));
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGHUP', () => gracefulShutdown('SIGHUP'));

// Final fallback for any other exit
process.on('exit', () => {
    if (port && port.isOpen) port.close();
});
// --- END UDP DISCOVERY ---
