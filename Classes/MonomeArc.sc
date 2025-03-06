/*
v.1.8
for information about monome devices:
https://monome.org

for further explanation of serialosc programming:
https://monome.org/docs/serialosc/osc/

written by:
joseph rangel, dani derks

*/

MonomeArc : Monome{

	var prefixID, scaleFactor, dvcID, oscout, deltaFunc, keyFunc, deltaFunc;

	*new { arg rotation, prefix;
		var fps, rotTranslate = [0,90,180,270];

		rotation = case
		{rotation == nil} {0}
		{rotation <= 3} {rotTranslate[rotation]}
		{rotation > 3} {rotation};

		prefix = case
		{prefix.isNil} {"/monomeArc"}
		{prefix.notNil} {prefix.asString};

		fps = case
		{fps.isNil} {60}
		{fps.notNil} {fps.asFloat};

		^ super.new.initArc(prefix, rotation, fps);
	}

	initArc { arg prefix_, rot_;

		"initializing arc".postln;
		prefixID = prefix_;
		rot = rot_;

		case
		{ rot == 0 } { scaleFactor = 0 }
		{ rot == 90 } { scaleFactor = 16 }
		{ rot == 180 } { scaleFactor = 32 }
		{ rot == 270 } { scaleFactor = 48 }
		{ ((rot == 0) or: (rot == 90) or: (rot == 180) or: (rot == 270)).not }
		{
			"Did not choose valid rotation. Using default: 0".warn;
			scaleFactor = 0;
			rot = 0;
		};

	}

	connectToPort { arg port;
		if( portlst.includes(port),{
			var idx = portlst.detectIndex({arg item, i; item == port});
			this.connect(idx);
		},{
			("no monome arc connected to specified port").warn;
		});
	}

	connectToSerial { arg serial;
		if( registeredDevices.includes(serial.asSymbol),{
			var idx = registeredDevices.detectIndex({arg item, i; item == serial});
			this.connect(idx);
		},{
			("no monome arc connected with specified serial").warn;
		});
	}

	connect { arg devicenum;
		if( devicenum == nil, {devicenum = 0});
		if(
			(portlst[devicenum].value).notNil
			&& (columns[devicenum].value).notNil,
			{
				if(
					(columns[devicenum] * rows[devicenum] == 0),
					{
						var prefixDiscover;

						Monome.buildOSCResponders;

						dvcID = devicenum;
						oscout = NetAddr.new("localhost", portlst[devicenum].value);
						Post << "MonomeArc: using device on port #" << portlst[devicenum].value << Char.nl;

						for(0, 3, { arg i; oscout.sendMsg(prefixID++"/ring/all", i, 0);});

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

						addCallbackComplete[dvcID] = false;
						addCallback.value(registeredDevices[dvcID], portlst[dvcID], prefixes[dvcID]);
						seroscnet.sendMsg("/serialosc/notify", "127.0.0.1", NetAddr.localAddr.port);
						isArc = true;
					},
					{
						("!! no monome arc detected at device slot " ++ devicenum).warn;
					}
				);
			},
			{
				("no monome arc detected at device slot " ++ devicenum).warn;
			}
		);
	}

	usePort { arg portnum;
		dvcID = portlst.indexOf(portnum);
		oscout = NetAddr.new("localhost", portnum);
		Post << "MonomeArc: using device # " << dvcID << Char.nl;

		oscout.sendMsg("/sys/port", NetAddr.localAddr.port);
		oscout.sendMsg("/sys/prefix", prefixID);
		oscout.sendMsg("/sys/rotation", rot);
	}

	delta { arg func;
		deltaFunc = OSCdef.newMatching(
			("deltaFunc_" ++ dvcID).asSymbol,
			{ arg message, time, addr, recvPort;
				var n = message[1], d = message[2];
				if( dvcID.notNil,{
					if( this.port.value() == addr.port, {
						func.value(n, d);
					});
				});
			},
			prefixID++"/enc/delta"
		);
	}

	key { arg func;
		keyFunc = OSCdef.newMatching(
			("keyFunc_" ++ dvcID).asSymbol,
			{ arg message, time, addr, recvPort;
				var n = message[1], s = message[2];
				if( dvcID.notNil,{
					if( this.port.value() == addr.port, {
						func.value(n, s);
					});
				});
			},
			prefixID++"/enc/key"
		);
	}

	ringset { | enc, led, lev |

		oscout.sendMsg(prefixID++"/ring/set", enc,
			(led + scaleFactor).wrap(0, 63),
			lev);
	}

	ringall { | enc, lev |
		oscout.sendMsg(prefixID++"/ring/all", enc, lev);
	}

	ringmap	{ | enc, larr |

		scaleFactor.do({

			larr = larr.shift(1, larr @ (larr.size - 1));

		});

		oscout.sendMsg(prefixID++"/ring/map", enc, *larr);

	}

	ringrange { | enc, led1, led2, lev |

		oscout.sendMsg(prefixID++"/ring/range", enc, led1+scaleFactor, led2+scaleFactor, lev);
	}

	// exercise caution when changing rotation
	// after change, your led positions may not be desirable.
	rot_ { arg degree;

		rot = degree;

		case
		{ rot == 0 } { scaleFactor = 0 }
		{ rot == 90 } { scaleFactor = 16 }
		{ rot == 180 } { scaleFactor = 32 }
		{ rot == 270 } { scaleFactor = 48 }
		{ (rot != 0) or: (rot != 90) or: (rot != 180) or: (rot != 270) } {

			"Did not choose valid rotation (0, 90, 180, 270). Using default: 0.".warn;
			scaleFactor = 0;
			rot = 0;
		};

		// flash one LED indicating north position
		for(0, 3, { arg i; this.ringall(i, 0);});

		4.do({
			for(0, 3, { arg i;

				for(0, 30, { arg brightness;

					this.ringset(i, 0, brightness.fold(0, 15));
				});

			});
		});

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

	prefix {
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

	cleanup {
		for(0, 3, { arg i; this.ringall(i, 0);});
		deltaFunc.free;
		keyFunc.free;
		oscout.disconnect;
	}

}