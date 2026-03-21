// Import required libraries
const express = require('express');
const cors = require('cors');
const { RtcTokenBuilder, RtcRole } = require('agora-token');

// Create Express app
const app = express();

// Enable CORS (allows Android app to connect)
app.use(cors());

// Parse JSON request bodies
app.use(express.json());

// ========================================
// CONFIGURATION - CHANGE THESE!
// ========================================
const APP_ID = "cc8e870e52474c5daca03ad0fb2fc81b";  // Your Agora App ID
const APP_CERTIFICATE = "e5c9ad636d8e4cc1b3a11818c9121836";  // ⚠️ CHANGE THIS!

// ========================================
// ENDPOINT: Generate Token
// ========================================
app.post('/generate-token', (req, res) => {
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log('📞 Token generation request received');
    
    // Get parameters from request
    const { channelName, uid } = req.body;
    
    // Validate parameters
    if (!channelName || !uid) {
        console.log('❌ Missing parameters');
        return res.status(400).json({ 
            error: 'channelName and uid are required' 
        });
    }
    
    console.log('Channel:', channelName);
    console.log('UID:', uid);
    
    try {
        // Token expires in 24 hours
        const expirationTimeInSeconds = 86400; // 24 * 60 * 60
        const currentTimestamp = Math.floor(Date.now() / 1000);
        const privilegeExpiredTs = currentTimestamp + expirationTimeInSeconds;
        
        // Generate token
        const token = RtcTokenBuilder.buildTokenWithUid(
            APP_ID,
            APP_CERTIFICATE,
            channelName,
            uid,
            RtcRole.PUBLISHER,
            privilegeExpiredTs
        );
        
        console.log('✅ Token generated successfully');
        console.log('   First 30 chars:', token.substring(0, 30) + '...');
        console.log('   Expires at:', new Date(privilegeExpiredTs * 1000).toLocaleString());
        console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
        
        // Send response
        res.json({ 
            token: token,
            expiresIn: expirationTimeInSeconds,
            expiresAt: privilegeExpiredTs,
            uid: uid,
            channelName: channelName
        });
        
    } catch (error) {
        console.error('❌ Token generation error:', error.message);
        res.status(500).json({ 
            error: 'Failed to generate token',
            details: error.message 
        });
    }
});

// ========================================
// TEST ENDPOINT: Check if server is running
// ========================================
app.get('/', (req, res) => {
    res.json({ 
        status: 'Server is running! 🚀',
        message: 'Use POST /generate-token to generate tokens',
        appId: APP_ID
    });
});

// ========================================
// START SERVER
// ========================================
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log('');
    console.log('════════════════════════════════════════');
    console.log('🚀 Agora Token Server Started!');
    console.log('════════════════════════════════════════');
    console.log('   Port:', PORT);
    console.log('   App ID:', APP_ID);
    console.log('   Token Expiration: 24 hours');
    console.log('');
    console.log('📡 Endpoints:');
    console.log('   GET  / - Server status');
    console.log('   POST /generate-token - Generate token');
    console.log('');
    console.log('⚠️  Make sure APP_CERTIFICATE is set!');
    console.log('════════════════════════════════════════');
    console.log('');
});
