CREATE TABLE IF NOT EXISTS hardware_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    level VARCHAR(10) DEFAULT 'INFO', -- INFO, WARN, ERR, DEBUG
    source VARCHAR(50) DEFAULT 'HARDWARE', -- e.g., ESP8266, ARDUINO, SERIAL_PARSER
    message TEXT NOT NULL,
    session_id INT NULL,
    INDEX (timestamp),
    INDEX (level),
    FOREIGN KEY (session_id) REFERENCES monitoring_sessions(id) ON DELETE SET NULL
);
