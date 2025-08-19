# 情侣定位应用配对服务器

这是情侣定位Android应用的配对服务器。它使用Socket.IO实现实时通信，帮助用户通过6位数字配对码进行配对和位置共享。

## 功能

- 生成6位数字配对码
- 帮助两个用户建立配对关系
- 实时转发位置数据
- 处理解除配对请求
- 自动清理过期的配对码

## 安装与运行

1. 确保已安装Node.js (建议v16或更高版本)

2. 安装依赖:
```
npm install
```

3. 启动服务器:
```
npm start
```

开发模式下运行(自动重启):
```
npm run dev
```

默认情况下，服务器运行在3000端口。

## API

本服务器使用Socket.IO提供WebSocket通信，没有REST API。

### Socket.IO事件

**用户端发送事件:**
- `generate_code`: 请求生成配对码
- `pair_with_code`: 提交配对码进行配对(参数: 6位配对码)
- `location_update`: 更新位置数据(参数: 位置信息对象)
- `unpair`: 请求解除配对

**服务器发送事件:**
- `pairing_code`: 返回生成的配对码
- `pairing_result`: 配对结果(参数: {success, message, pairId})
- `partner_location`: 伴侣位置更新(参数: 位置信息对象)
- `partner_unpaired`: 伴侣解除了配对
- `partner_disconnected`: 伴侣断开连接
- `unpair_success`: 解除配对成功

## 部署

服务器可以部署在任何支持Node.js的平台上，如Heroku、Vercel、AWS等。

注意事项:
- 在生产环境中，建议配置环境变量来设置端口(`PORT`)
- 考虑添加身份验证和安全措施
- 为稳定性考虑，建议使用PM2等进程管理工具 