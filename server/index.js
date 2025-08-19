const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const { v4: uuidv4 } = require('uuid');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*", // 允许所有域名连接
    methods: ["GET", "POST"]
  }
});

// 存储配对码和对应的用户信息
const pairingCodes = new Map();
// 存储已配对的用户
const pairedUsers = new Map();

// 生成6位数字配对码
function generatePairingCode() {
  // 生成6位随机数字
  return Math.floor(100000 + Math.random() * 900000).toString();
}

// 清理过期的配对码（5分钟后过期）
function cleanupExpiredCodes() {
  const now = Date.now();
  for (const [code, info] of pairingCodes.entries()) {
    if (now - info.timestamp > 5 * 60 * 1000) {
      pairingCodes.delete(code);
      console.log(`配对码 ${code} 已过期`);
    }
  }
}

// 每分钟清理一次过期的配对码
setInterval(cleanupExpiredCodes, 60 * 1000);

io.on('connection', (socket) => {
  console.log('用户连接: ' + socket.id);
  
  // 用户请求生成配对码
  socket.on('generate_code', () => {
    let code = generatePairingCode();
    
    // 确保配对码不重复
    while (pairingCodes.has(code)) {
      code = generatePairingCode();
    }
    
    // 存储配对码和用户信息
    pairingCodes.set(code, {
      socketId: socket.id,
      timestamp: Date.now()
    });
    
    console.log(`生成配对码: ${code} 给用户 ${socket.id}`);
    
    // 发送配对码给用户
    socket.emit('pairing_code', code);
  });
  
  // 用户输入配对码尝试配对
  socket.on('pair_with_code', (code) => {
    console.log(`用户 ${socket.id} 尝试使用配对码 ${code} 进行配对`);
    
    if (!pairingCodes.has(code)) {
      socket.emit('pairing_result', {
        success: false,
        message: '无效的配对码'
      });
      return;
    }
    
    const pairInfo = pairingCodes.get(code);
    const partnerSocketId = pairInfo.socketId;
    
    // 如果用户尝试和自己配对
    if (partnerSocketId === socket.id) {
      socket.emit('pairing_result', {
        success: false,
        message: '不能与自己配对'
      });
      return;
    }
    
    // 为两个用户创建唯一的配对ID
    const pairId = uuidv4();
    
    // 记录配对关系
    pairedUsers.set(socket.id, {
      partnerId: partnerSocketId,
      pairId: pairId
    });
    
    pairedUsers.set(partnerSocketId, {
      partnerId: socket.id,
      pairId: pairId
    });
    
    // 通知双方配对成功
    socket.emit('pairing_result', {
      success: true,
      message: '配对成功',
      pairId: pairId
    });
    
    io.to(partnerSocketId).emit('pairing_result', {
      success: true,
      message: '配对成功',
      pairId: pairId
    });
    
    // 删除已使用的配对码
    pairingCodes.delete(code);
    
    console.log(`用户 ${socket.id} 和 ${partnerSocketId} 配对成功`);
  });
  
  // 位置共享
  socket.on('location_update', (locationData) => {
    if (!pairedUsers.has(socket.id)) {
      return;
    }
    
    const { partnerId } = pairedUsers.get(socket.id);
    io.to(partnerId).emit('partner_location', locationData);
  });
  
  // 解除配对
  socket.on('unpair', () => {
    if (!pairedUsers.has(socket.id)) {
      return;
    }
    
    const { partnerId } = pairedUsers.get(socket.id);
    
    // 通知对方解除配对
    if (io.sockets.sockets.has(partnerId)) {
      io.to(partnerId).emit('partner_unpaired');
    }
    
    // 删除配对记录
    pairedUsers.delete(partnerId);
    pairedUsers.delete(socket.id);
    
    socket.emit('unpair_success');
    console.log(`用户 ${socket.id} 解除了与 ${partnerId} 的配对`);
  });
  
  // 断开连接时，清理配对关系
  socket.on('disconnect', () => {
    console.log('用户断开连接: ' + socket.id);
    
    // 检查是否有配对关系
    if (pairedUsers.has(socket.id)) {
      const { partnerId } = pairedUsers.get(socket.id);
      
      // 通知对方解除配对
      if (io.sockets.sockets.has(partnerId)) {
        io.to(partnerId).emit('partner_disconnected');
      }
      
      // 删除配对记录
      pairedUsers.delete(partnerId);
      pairedUsers.delete(socket.id);
    }
    
    // 删除该用户生成的配对码
    for (const [code, info] of pairingCodes.entries()) {
      if (info.socketId === socket.id) {
        pairingCodes.delete(code);
        console.log(`删除用户 ${socket.id} 的配对码 ${code}`);
      }
    }
  });
});

// 启动服务器
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`服务器运行在端口 ${PORT}`);
}); 