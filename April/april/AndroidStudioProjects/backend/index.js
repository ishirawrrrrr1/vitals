const express = require('express');
const mysql = require('mysql2/promise');
const cors = require('cors');
const http = require('http');
const { Server } = require('socket.io');
require('dotenv').config();

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: "*", methods: ["GET", "POST"] }
});
const port = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static('public')); // Serve the Admin GUI

// XAMPP MySQL Connection
const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_NAME || 'vitals_db',
};

// Create tables if they don't exist
const initDb = async () => {
  try {
    const connection = await mysql.createConnection({
        host: dbConfig.host,
        user: dbConfig.user,
        password: dbConfig.password
    });
    await connection.query(`CREATE DATABASE IF NOT EXISTS ${dbConfig.database}`);
    await connection.end();

    const pool = mysql.createPool(dbConfig);    
    await pool.query(`
      CREATE TABLE IF NOT EXISTS users (
        id INT AUTO_INCREMENT PRIMARY KEY,
        email VARCHAR(255) UNIQUE NOT NULL,
        password VARCHAR(255) NOT NULL,
        name VARCHAR(255),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    `);    
    await pool.query(`
      CREATE TABLE IF NOT EXISTS vitals (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id INT,
        session_id INT NULL,
        heart_rate FLOAT,
        temperature FLOAT,
        spo2 FLOAT,
        blood_pressure VARCHAR(50),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);
    
    // Add session_id column safely if it didn't exist in older table
    try {
        await pool.query("ALTER TABLE vitals ADD COLUMN session_id INT NULL");
    } catch(e) {} // Ignore if already exists

    await pool.query(`
      CREATE TABLE IF NOT EXISTS sessions (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id INT,
        status VARCHAR(50) DEFAULT 'INITIAL',
        intensity VARCHAR(50),
        duration_mins INT,
        start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        end_time TIMESTAMP NULL,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    await pool.query(`
      CREATE TABLE IF NOT EXISTS config (
        id INT PRIMARY KEY DEFAULT 1,
        temp_offset FLOAT DEFAULT 0.0,
        bpm_offset INT DEFAULT 0,
        spo2_offset INT DEFAULT 0,
        last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
      )
    `);

    // Initialize config row if missing
    await pool.query("INSERT IGNORE INTO config (id, temp_offset, bpm_offset, spo2_offset) VALUES (1, 0, 0, 0)");

    console.log('XAMPP MySQL Database and tables initialized');
    return pool;
  } catch (err) {
    console.error('Error initializing database:', err);
  }
};

let pool;
initDb().then(p => pool = p);

// Login Route
app.post('/api/login', async (req, res) => {
  const { email, password } = req.body;
  console.log(`[AUTH_DEBUG] Login attempt for: ${email}`);
  try {
    const [rows] = await pool.query(
      'SELECT * FROM users WHERE email = ? AND password = ?',
      [email, password]
    );
    if (rows.length > 0) {
      console.log(`[AUTH_DEBUG] Login successful for user ID: ${rows[0].id}`);
      res.json({ success: true, user: rows[0] });
    } else {
      console.warn(`[AUTH_DEBUG] Login failed for email: ${email}`);
      res.status(401).json({ success: false, message: 'Invalid email or password' });
    }
  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// Signup Route
app.post('/api/signup', async (req, res) => {
    const { email, password, name } = req.body;
    try {
      const [result] = await pool.query(
        'INSERT INTO users (email, password, name) VALUES (?, ?, ?)',
        [email, password, name]
      );
      res.json({ success: true, userId: result.insertId });
    } catch (err) {
      if (err.code === 'ER_DUP_ENTRY') {
        res.status(400).json({ success: false, message: 'Email already exists' });
      } else {
        console.error(err);
        res.status(500).json({ success: false, message: 'Server error' });
      }
    }
  });

// Vital Signs Routes
app.get('/api/vitals', async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT * FROM vitals ORDER BY created_at DESC LIMIT 10');
    res.json(rows);
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

app.post('/api/vitals', async (req, res) => {
  const { user_id, heart_rate, temperature, spo2, blood_pressure } = req.body;
  const uid = user_id || 1;
  console.log(`[MONITORING_DEBUG] Incoming vitals for user ${uid}: HR=${heart_rate}, T=${temperature}`);
  try {
    // Look for active session
    let sessionId = null;
    const [sessions] = await pool.query("SELECT id FROM sessions WHERE user_id=? AND status IN ('INITIAL', 'RUNNING') ORDER BY id DESC LIMIT 1", [uid]);
    if (sessions.length > 0) sessionId = sessions[0].id;

    const [result] = await pool.query(
      'INSERT INTO vitals (user_id, session_id, heart_rate, temperature, spo2, blood_pressure) VALUES (?, ?, ?, ?, ?, ?)',
      [uid, sessionId, heart_rate, temperature, spo2, blood_pressure]
    );
    res.json({ id: result.insertId, session_id: sessionId, ...req.body });
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

// Bulk Sync API (v6.2 Support)
app.post('/api/bulkBackup', async (req, res) => {
    const vitals = req.body;
    if (!Array.isArray(vitals)) return res.status(400).json({ success: false, message: 'Expected array' });
    
    console.log(`[SYNC] Bulk backup received: ${vitals.length} records`);
    try {
        const connection = await pool.getConnection();
        try {
            await connection.beginTransaction();
            for (const v of vitals) {
                // Determine column based on metric type if generic
                let hr = null, temp = null, spo2 = null;
                if (v.metric && v.metric.toLowerCase().includes('heart')) hr = v.value;
                else if (v.metric && v.metric.toLowerCase().includes('temp')) temp = v.value;
                else if (v.metric && v.metric.toLowerCase().includes('spo2')) spo2 = v.value;
                else { hr = v.heart_rate; temp = v.temperature; spo2 = v.spo2; }

                await connection.query(
                    'INSERT IGNORE INTO vitals (user_id, session_id, heart_rate, temperature, spo2, created_at) VALUES (?, ?, ?, ?, ?, ?)',
                    [v.user_id || 1, v.sessionId || v.session_id, hr, temp, spo2, new Date(v.timestamp || Date.now())]
                );
            }
            await connection.commit();
            res.json({ success: true, count: vitals.length });
        } catch (err) {
            await connection.rollback();
            throw err;
        } finally {
            connection.release();
        }
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false, message: 'Bulk Sync Failed' });
    }
});

app.post('/api/syncSessions', async (req, res) => {
    const sessions = req.body;
    if (!Array.isArray(sessions)) return res.status(400).json({ success: false, message: 'Expected array' });
    
    console.log(`[SYNC] Syncing ${sessions.length} sessions`);
    try {
        for (const s of sessions) {
            await pool.query(
                'INSERT IGNORE INTO sessions (id, user_id, status, intensity, duration_mins, start_time) VALUES (?, ?, ?, ?, ?, ?)',
                [s.id, s.user_id || 1, 'COMPLETED', s.intensity, s.duration_mins, new Date(s.timestamp || Date.now())]
            );
        }
        res.json({ success: true });
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false });
    }
});

app.get('/api/recoverSync/:userId', async (req, res) => {
    const { userId } = req.params;
    try {
        const [vitals] = await pool.query('SELECT * FROM vitals WHERE user_id = ? ORDER BY created_at DESC LIMIT 500', [userId]);
        // Format for App's LocalVitalSign model
        const formatted = vitals.map(v => ({
            id: v.id,
            sensorType: v.heart_rate ? 'MAX30102' : 'BodyTemp',
            metric: v.heart_rate ? 'Heart_Rate' : 'Temperature',
            value: v.heart_rate || v.temperature || v.spo2,
            unit: v.heart_rate ? 'bpm' : 'C',
            timestamp: new Date(v.created_at).getTime(),
            sessionId: v.session_id,
            isSynced: true
        }));
        res.json(formatted);
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false });
    }
});

app.get('/api/vitals/:userId', async (req, res) => {
    const { userId } = req.params;
    try {
        const [rows] = await pool.query(
            'SELECT heart_rate, temperature, spo2, blood_pressure FROM vitals WHERE user_id = ? ORDER BY created_at DESC LIMIT 1',
            [userId]
        );
        if (rows.length > 0) {
            res.json(rows);
        } else {
            // Return dummy data if no data exists for the user yet
            res.json([{
                heart_rate: 72,
                temperature: 36.5,
                spo2: 98,
                blood_pressure: "120/80"
            }]);
        }
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false, message: 'Server error' });
    }
});

// Session Management Routes
app.post('/api/sessions/start', async (req, res) => {
    const { user_id, intensity, duration_mins } = req.body;
    const uid = user_id || 1; // Default to 1
    const duration = parseInt(duration_mins) || 10;
    
    try {
        // Stop any old running sessions
        await pool.query("UPDATE sessions SET status='COMPLETED', end_time=CURRENT_TIMESTAMP WHERE user_id=? AND status IN ('INITIAL', 'RUNNING')", [uid]);
        
        const [result] = await pool.query(
            "INSERT INTO sessions (user_id, status, intensity, duration_mins) VALUES (?, 'INITIAL', ?, ?)",
            [uid, intensity, duration]
        );
        const sessionId = result.insertId;
        
        // State Machine: Transition to RUNNING after 10 seconds
        setTimeout(async () => {
            try {
                await pool.query("UPDATE sessions SET status='RUNNING' WHERE id=? AND status='INITIAL'", [sessionId]);
                console.log(`Session ${sessionId} transitioned to RUNNING`);
            } catch(e) { console.error("Error setting RUNNING status", e); }
        }, 10000);
        
        // State Machine: Set COMPLETED after (10s + duration_mins)
        const totalDurationMs = 10000 + (duration * 60 * 1000);
        setTimeout(async () => {
            try {
                await pool.query("UPDATE sessions SET status='COMPLETED', end_time=CURRENT_TIMESTAMP WHERE id=?", [sessionId]);
                console.log(`Session ${sessionId} transitioned to COMPLETED`);
            } catch(e) { console.error("Error setting COMPLETED status", e); }
        }, totalDurationMs);

        res.json({ success: true, session_id: sessionId, status: 'INITIAL' });
    } catch(err) {
        console.error(err);
        res.status(500).json({ success: false, message: 'Server error' });
    }
});

app.get('/api/sessions/current/:userId', async (req, res) => {
    const { userId } = req.params;
    try {
        const [sessions] = await pool.query("SELECT * FROM sessions WHERE user_id=? ORDER BY id DESC LIMIT 1", [userId]);
        if(sessions.length === 0) return res.json({ status: 'NONE' });
        
        const session = sessions[0];
        
        // Calculate running averages
        const [averages] = await pool.query(
            "SELECT AVG(heart_rate) as avg_hr, AVG(temperature) as avg_temp, AVG(spo2) as avg_spo2 FROM vitals WHERE session_id=?", 
            [session.id]
        );
        
        res.json({
            session_id: session.id,
            status: session.status,
            intensity: session.intensity,
            duration_mins: session.duration_mins,
            start_time: session.start_time,
            averages: averages[0] || {}
        });
    } catch(err) {
        console.error(err);
        res.status(500).json({ success: false, message: 'Server error' });
    }
});

// Admin Config Routes
app.get('/api/admin/config', async (req, res) => {
    try {
        const [rows] = await pool.query('SELECT * FROM config WHERE id = 1');
        res.json(rows[0]);
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false, message: 'Server error' });
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
        console.log(`[FIXER] Offsets successfully updated in database.`);
        res.json({ success: true, message: 'Configuration updated' });
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false, message: 'Server error' });
    }
});

// Socket.io Implementation (Forwarding Vitals to Dashboard)
io.on('connection', (socket) => {
    console.log(`[SOCKET] Client connected: ${socket.id}`);

    socket.on('join_hardware', (data) => {
        socket.join('hardware_room');
        console.log(`[SOCKET] Hardware device joined: ${data.device_id}`);
    });

    socket.on('join_dashboard', (data) => {
        socket.join('dashboard_room');
        console.log(`[SOCKET] Admin Dashboard joined`);
    });

    socket.on('sensor_update', (data) => {
        // Broadcast to all dashboard clients
        io.to('dashboard_room').emit('vitals_update', data);
        
        // Log critical signals locally for debugging
        if (typeof data.raw === 'string' && data.raw.includes('SpO2')) {
            console.log(`[LIVE_DATA] Forwarding SpO2: ${data.raw}`);
        }
    });

    socket.on('hardware_command', (data) => {
        // Forward commands (like START/STOP) to the hardware room
        io.to('hardware_room').emit('hardware_command', data);
    });

    socket.on('disconnect', () => {
        console.log(`[SOCKET] Client disconnected: ${socket.id}`);
    });
});

server.listen(port, () => {
    console.log(`Backend server running on port ${port}`);
});
