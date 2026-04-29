const mysql = require('mysql2/promise');
require('dotenv').config();

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_NAME || 'vitals_db',
  port: process.env.DB_PORT || 3306,
};

async function testConnection() {
  console.log('--- Testing Connection to XAMPP MySQL ---');
  console.log(`Host: ${dbConfig.host}`);
  console.log(`User: ${dbConfig.user}`);
  console.log(`Database: ${dbConfig.database}`);

  try {
    const connection = await mysql.createConnection({
        host: dbConfig.host,
        user: dbConfig.user,
        password: dbConfig.password,
        port: dbConfig.port
    });
    console.log('✅ Successfully connected to MySQL server!');

    await connection.query(`CREATE DATABASE IF NOT EXISTS ${dbConfig.database}`);
    console.log(`✅ Database "${dbConfig.database}" is ready.`);

    await connection.changeUser({ database: dbConfig.database });

    const [rows] = await connection.query('SHOW TABLES');
    console.log(`✅ Found ${rows.length} tables in the database.`);

    await connection.end();
    console.log('--- Test Complete ---');
  } catch (err) {
    console.error('❌ Connection Failed!');
    console.error(`Error Code: ${err.code}`);
    console.error(`Error Message: ${err.message}`);

    if (err.code === 'ECONNREFUSED') {
      console.error('\nSUGGESTION: Make sure XAMPP is running and the "MySQL" module is started in the XAMPP Control Panel.');
    } else if (err.code === 'ER_ACCESS_DENIED_ERROR') {
      console.error('\nSUGGESTION: Check your DB_USER and DB_PASSWORD in the .env file.');
    }
  }
}

testConnection();
