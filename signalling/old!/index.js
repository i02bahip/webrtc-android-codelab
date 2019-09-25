'use strict';

var os = require('os');
var nodeStatic = require('node-static');
var https = require('https');
var socketIO = require('socket.io');
var fs = require("fs");
var options = {
  key: fs.readFileSync('/etc/letsencrypt/live/pbh.sytes.net/privkey.pem'),
	cert: fs.readFileSync('/etc/letsencrypt/live/pbh.sytes.net/fullchain.pem')
};

var fileServer = new(nodeStatic.Server)();
var app = https.createServer(options,function(req, res) {
  fileServer.serve(req, res);
}).listen(8443);

var roomFic = '';

var io = socketIO.listen(app);
io.sockets.on('connection', function(socket) {
  // convenience function to log server messages on the client 
  function log() {
    var array = ['Message from server LOG:'];
    array.push.apply(array, arguments);
    console.log(array);
    socket.emit('log', array);
  }

  socket.on('message', function(message) {
    if (roomFic !== '') {
      log('Client said: ', message);
      socket.broadcast.emit('message', message);  
    }
  });

  socket.on('create or join', function(room) {
    log('Received request to create or join room ' + room);
    roomFic = room;
    var numClients = io.sockets.sockets.length;      
    log('Room ' + roomFic + ' now has ' + numClients + ' client(s)');

    if (numClients === 1) {
      socket.join(roomFic);
      log('Client ID ' + socket.id + ' created room ' + roomFic);
      socket.broadcast.emit('created', socket.id);

    } else if (numClients === 2) {
      log('Client ID ' + socket.id + ' joined room ' + roomFic);
      io.sockets.in(roomFic).emit('join', roomFic);
      socket.join(roomFic);
      socket.emit('joined', roomFic, socket.id);
      io.sockets.in(roomFic).emit('ready');
    } else {
      socket.emit('full', roomFic);
    }
  });

  socket.on('ipaddr', function() {
    var ifaces = os.networkInterfaces();
    for (var dev in ifaces) {
      ifaces[dev].forEach(function(details) {
        if (details.family === 'IPv4' && details.address !== '127.0.0.1') {
          socket.emit('ipaddr', roomFic, details.address);
        }
      });
    }
  });

  socket.on('bye', function(){
    console.log('received bye'); 
  });
});
