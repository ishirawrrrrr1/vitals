// ===== ADD THESE ENDPOINTS TO backend/index.js =====
// Place them after the existing sensor endpoints (around line 1000)

/**
 * RELAY CONTROL ENDPOINTS
 * Allow Android app to send commands to Arduino MEGA
 */

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
        
        if (!result[0] || result[0].length === 0) {
            return res.json({
                arm_moving: false,
                leg_moving: false,
                glove_active: false,
                status: 'NO_DATA'
            });
        }
        
        res.json(result[0][0]);
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
        
        res.json(result[0]);
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

// ===== DATABASE MIGRATION =====
// Run this to create the control_logs table:
/*

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
);

*/

// ===== ALSO UPDATE the vitals table to include actuator state =====
/*

ALTER TABLE vitals ADD COLUMN arm_moving BOOLEAN DEFAULT FALSE;
ALTER TABLE vitals ADD COLUMN leg_moving BOOLEAN DEFAULT FALSE;
ALTER TABLE vitals ADD COLUMN glove_active BOOLEAN DEFAULT FALSE;

*/
