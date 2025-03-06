/*
v.1.8
for information about monome devices:
https://monome.org

for further explanation of serialosc programming:
https://monome.org/docs/serialosc/osc/

written by:
raja das, ezra buchla, dani derks

*/

MonomeGrid : Monome{

	var prefixID, rot, fpsVal, dvcID, keyFunc, oscout, isArc; // instance variables

	*new { arg rotation, prefix, fps;
		var rotTranslate = [0,90,180,270];

		rotation = case
		{rotation == nil} {0}
		{rotation <= 3} {rotTranslate[rotation]}
		{rotation > 3} {rotation};

		prefix = case
		{prefix.isNil} {"/monome"}
		{prefix.notNil} {prefix.asString};

		fps = case
		{fps.isNil} {60}
		{fps.notNil} {fps.asFloat};

		^ super.new.initGrid(prefix, rotation, fps);
	}

	initGrid { arg prefix_, rot_, fps_;
		"initializing grid".postln;
		prefixID = prefix_;
		rot = rot_;
		fpsVal = fps_;
	}

	connectToPort { arg port;
		if( portlst.includes(port),{
			var idx = portlst.detectIndex({arg item, i; item == port});
			this.connect(idx);
		},{
			("no monome grid connected to specified port").warn;
		});
	}

	connectToSerial { arg serial;
		if( registeredDevices.includes(serial.asSymbol),{
			var idx = registeredDevices.detectIndex({arg item, i; item == serial});
			this.connect(idx);
		},{
			("no monome grid connected with specified serial").warn;
		});
	}

	connect { arg devicenum;
		if( devicenum == nil, {devicenum = 0});
		if( (portlst[devicenum].value).notNil, {

			var prefixDiscover;

			Monome.buildOSCResponders;

			dvcID = devicenum;
			oscout = NetAddr.new("localhost", portlst[devicenum].value);
			Post << "MonomeGrid: using device on port #" << portlst[devicenum].value << Char.nl;

			oscout.sendMsg(prefixID++"/grid/led/all", 0);

			prefixes[devicenum] = prefixID;

			prefixDiscover.free;
			prefixDiscover = OSCdef.newMatching(\monomeprefix,
				{|msg, time, addr, recvPort|
					prefixes[devicenum] = prefixID;
			}, '/sys/prefix', oscout);

			oscout.sendMsg("/sys/port", NetAddr.localAddr.port);
			oscout.sendMsg("/sys/prefix", prefixID);
			oscout.sendMsg("/sys/rotation", rot);
			oscout.sendMsg("/sys/info");

			// collect individual LED messages into a 'map':
			quadDirty[dvcID] = Array.fill(8,{0});
			ledQuads[dvcID] = Array.fill(8,{Array.fill(64,{0})});

			redrawTimers[dvcID].stop;

			redrawTimers[dvcID] = Routine({
				var interval = 1/fpsVal,
				offsets = [
					[0,0],[8,0],[0,8],[8,8]
				],
				max = case
				{(rows[dvcID] == 8) && (columns[dvcID] == 8)}{0}
				{(rows[dvcID] == 8) && (columns[dvcID] == 16)}{1}
				{(rows[dvcID] == 16) && (columns[dvcID] == 16)}{3};

				loop {
					if( (portlst[devicenum].value).notNil,{
						for (0, max, {
							arg i;
							if(quadDirty[dvcID][i] != 0,
								{
									oscout.sendMsg(
										prefixID++"/grid/led/level/map",
										offsets[i][0],
										offsets[i][1],
										*ledQuads[dvcID][i]
									);
									quadDirty[dvcID][i] = 0;
								}
							);
						});
					});

					interval.yield;
				}

			});

			redrawTimers[dvcID].play();
			addCallbackComplete[dvcID] = false;
			addCallback.value(registeredDevices[dvcID], portlst[dvcID], prefixes[dvcID]);
			seroscnet.sendMsg("/serialosc/notify", "127.0.0.1", NetAddr.localAddr.port);
		},{
			("no monome grid detected at device slot " ++ devicenum).warn;
		});
	}

	usePort { arg portnum;
		dvcID = portlst.indexOf(portnum);
		oscout = NetAddr.new("localhost", portnum);
		Post << "MonomeGrid: using device # " << dvcID << Char.nl;

		oscout.sendMsg("/sys/port", NetAddr.localAddr.port);
		oscout.sendMsg("/sys/prefix", prefixID);
		oscout.sendMsg("/sys/rotation", rot);
	}

	port {
		if( dvcID.notNil, {
			^portlst[dvcID];
		},{
			^nil;
		});
	}

	rows {
		if( dvcID.notNil, {
			^rows[dvcID];
		},{
			^nil;
		});
	}

	cols {
		if( dvcID.notNil, {
			^columns[dvcID];
		},{
			^nil;
		});
	}

	serial {
		if( dvcID.notNil, {
			^registeredDevices[dvcID];
		},{
			^nil;
		});
	}

	prefix{
		if( dvcID.notNil, {
			^prefixes[dvcID];
		},{
			^nil;
		});
	}

	rotation {
		if( dvcID.notNil, {
			^rot
		},{
			^nil;
		});
	}

	fps {
		if( dvcID.notNil, {
			^fpsVal
		},{
			^nil;
		});
	}

	dvcnum {
		^dvcID;
	}

	key { arg func;
		keyFunc = OSCdef.newMatching(
			("keyFunc_" ++ dvcID).asSymbol,
			{ arg message, time, addr, recvPort;
				var x = message[1], y = message[2], z = message[3];
				if( dvcID.notNil,{
					if( this.port.value() == addr.port, {
						func.value(x,y,z);
					});
				});
			},
			prefixID++"/grid/key"
		);
	}

	led { arg x,y,val;
		var offset;
		case
		// 64: quad 0 (top left)
		{(x < 8) && (y < 8)} {
			offset = (8*y)+x;
			ledQuads[dvcID][0][offset] = val;
			quadDirty[dvcID][0] = 1;
		}
		// 128: quad 1 (top right)
		{(x > 7) && (x < 16) && (y < 8)} {
			offset = (8*y)+(x-8);
			ledQuads[dvcID][1][offset] = val;
			quadDirty[dvcID][1] = 1;
		}
		// 256: quad 2 (bottom left)
		{(x < 8) && (y > 7) && (y < 16)} {
			offset = (8*(y-8))+x;
			ledQuads[dvcID][2][offset] = val;
			quadDirty[dvcID][2] = 1;
		}
		// 256: quad 3 (bottom right)
		{(x > 7) && (x < 16) && (y > 7) && (y < 16)} {
			offset = (8*(y-8))+(x-8);
			ledQuads[dvcID][3][offset] = val;
			quadDirty[dvcID][3] = 1;
		}
	}

	all { arg val;
		oscout.sendMsg(prefixID++"/grid/led/level/all", val);
		ledQuads[dvcID].do({
			arg item,table;
			ledQuads[dvcID][table].do({
				arg i,slot;
				ledQuads[dvcID][table][slot] = val;
			});
		});
	}

	ledset	{ arg x, y, state;
		if ((state == 0) or: (state == 1)) {
			oscout.sendMsg(prefixID++"/grid/led/set", x, y, state);
			this.led(x, y, state*15);
		} {
			"invalid argument (state must be 0 or 1).".warn;
		};
	}

	intensity	{ arg val;
		oscout.sendMsg(prefixID++"/grid/led/intensity", val);
	}

	tilt_enable { arg device, state;
		oscout.sendMsg(prefixID++"/tilt/set", device, state);
	}

	cleanup {
		this.all(0);
		keyFunc.free;
		oscout.disconnect;
	}
}