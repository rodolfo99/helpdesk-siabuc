const port = process.env.SERVER_PORT || '8080';
if (!/^\d+$/.test(port) || Number(port) < 1 || Number(port) > 65535) throw new Error('SERVER_PORT debe ser un puerto válido');
module.exports = {'/api': {target: `http://127.0.0.1:${port}`, secure: false, changeOrigin: true}};
