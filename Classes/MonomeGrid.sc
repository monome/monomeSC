/*

for information about monome devices:
monome.org

written by:
raja das, ezra buchla, dan derks

*/

MonomeGrid {

    classvar seroscnet, <>discovery, <>rows, <>columns, <>portlst, quadDirty, ledQuads, redrawTimer;
	var <>prefix, <>rot, <>dvcnum, oscout;

    *initClass {

        var sz, rw, cl;

        portlst = List.new(0);
        rows = List.new(0);
        columns = List.new(0);
        seroscnet = NetAddr.new("localhost", 12002);
        seroscnet.sendMsg("/serialosc/list", "127.0.0.1", NetAddr.localAddr.port);

		quadDirty = Dictionary.new;
		ledQuads = Dictionary.new;
		redrawTimer = Dictionary.new;

        StartUp.add {

            discovery = OSCdef.newMatching(\monomediscover,
                {|msg, time, addr, recvPort|

                    sz = msg[2].asString.replace("monome","").replace("40h",64).asInteger;
                    rw = case
                    {sz == 64} {8}
                    {sz == 128}{8}
                    {sz == 256}{16}
					{sz == 512}{16};
                    cl = case
                    {sz == 64} {8}
                    {sz == 128}{16}
                    {sz == 256}{16}
					{sz == 512}{32};
                    rows.add(rw);
                    columns.add(cl);
                    portlst.add(msg[3]);
                    ("Device connected on port:"++msg[3]).postln;
                    msg[1].postln;
                    msg[2].postln;

            }, '/serialosc/device', seroscnet);

        }

    }

    *new { arg rot;
		var rotTranslate = [0,90,180,270];

		rot = case
		{rot == nil} {0}
		{rot <= 3} {rotTranslate[rot]}
		{rot > 3} {rot};

		^ super.new.init("/monome", rot);
    }

    init { arg prefix_, rot_;
        prefix = prefix_;
        rot = rot_;
    }

    connect { arg devicenum;
		if( devicenum == nil, {devicenum = 0});
        dvcnum = devicenum;
        oscout = NetAddr.new("localhost", portlst[devicenum].value);
        Post << "Using device on port#" << portlst[devicenum].value << Char.nl;

		oscout.sendMsg(prefix++"/grid/led/all", 0);

        oscout.sendMsg("/sys/port", NetAddr.localAddr.port);
        oscout.sendMsg("/sys/prefix", prefix);
        oscout.sendMsg("/sys/rotation", rot);

		// collect individual LED messages into a 'map':
		quadDirty[dvcnum] = Array.fill(8,{0});
		ledQuads[dvcnum] = Array.fill(8,{Array.fill(64,{0})});

		redrawTimer[dvcnum] = Routine({
			var interval = 1/60,
			offsets = [
				[0,0],[8,0],[0,8],[8,8],[16,0][24,0],[16,8],[24,8]
			],
			max = case
			{(rows[dvcnum] == 8) && (columns[dvcnum] == 8)}{0}
			{(rows[dvcnum] == 8) && (columns[dvcnum] == 16)}{1}
			{(rows[dvcnum] == 16) && (columns[dvcnum] == 16)}{3}
			{(rows[dvcnum] == 16) && (columns[dvcnum] == 32)}{7};
			loop {
				for (0, 1, {
					arg i;
					if(quadDirty[dvcnum][i] != 0,
						{
							oscout.sendMsg(
								prefix++"/grid/led/level/map",
								offsets[i][0],
								offsets[i][1],
								*ledQuads[dvcnum][i]
							);
							quadDirty[dvcnum][i] = 0;
						}
					);
					interval.yield;
				});
			}
		});

		redrawTimer[dvcnum].play();
    }

    usePort { arg portnum;
        dvcnum = portlst.indexOf(portnum);
        oscout = NetAddr.new("localhost", portnum);
        Post << "Using device#" << dvcnum << Char.nl;

        oscout.sendMsg("/sys/port", NetAddr.localAddr.port);
        oscout.sendMsg("/sys/prefix", prefix);
        oscout.sendMsg("/sys/rotation", rot);
    }

	port {
        ^portlst[dvcnum];
    }

    rows {
        ^rows[dvcnum];
    }

    cols {
        ^columns[dvcnum];
    }

	key { arg func;
		OSCFunc.newMatching(
			{ arg message, time, addr, recvPort;
				var x = message[1], y = message[2], z = message[3];
				if( portlst[dvcnum] == addr.port, {
					func.value(x,y,z);
				});
			},
			prefix++"/grid/key"
		);
	}

	led { arg x,y,val;
		var offset;
		case
		// 64: quad01 (top left)
		{(x < 8) && (y < 8)} {
			offset = (8*y)+x;
			ledQuads[dvcnum][0][offset] = val;
			quadDirty[dvcnum][0] = 1;
		}
		// 128: quad 1 (top right)
		{(x > 7) && (x < 16) && (y < 8)} {
			offset = (8*y)+(x-8);
			ledQuads[dvcnum][1][offset] = val;
			quadDirty[dvcnum][1] = 1;
		}
		// 256: quad 2 (bottom left)
		{(x < 8) && (y > 7) && (y < 16)} {
			offset = (8*(y-8))+x;
			ledQuads[dvcnum][2][offset] = val;
			quadDirty[dvcnum][2] = 1;
		}
		// 256: quad 3 (bottom right)
		{(x > 7) && (x < 16) && (y > 7) && (y < 16)} {
			offset = (8*(y-8))+(x-8);
			ledQuads[dvcnum][3][offset] = val;
			quadDirty[dvcnum][3] = 1;
		}
		// 512: quad 4 (top mid-right)
		{(x > 15) && (x < 24) && (y < 8)} {
			offset = (8*y)+(x-16);
			ledQuads[dvcnum][4][offset] = val;
			quadDirty[dvcnum][4] = 1;
		}
		// 512: quad 5 (top far right)
		{(x > 23) && (x < 32) && (y < 8)} {
			offset = (8*y)+(x-24);
			ledQuads[dvcnum][5][offset] = val;
			quadDirty[dvcnum][5] = 1;
		}
		// 512: quad 6 (bottom mid-right)
		{(x > 15) && (x < 24) && (y > 7) && (y < 16)} {
			offset = (8*(y-8))+(x-16);
			ledQuads[dvcnum][6][offset] = val;
			quadDirty[dvcnum][6] = 1;
		}
		// 512: quad 7 (bottom far right)
		{(x > 23) && (x < 32) && (y > 7) && (y < 16)} {
			offset = (8*(y-8))+(x-24);
			ledQuads[dvcnum][7][offset] = val;
			quadDirty[dvcnum][7] = 1;
		}
	}

	all { arg val;
		oscout.sendMsg(prefix++"/grid/led/level/all", val);
	}

    // See here: http://monome.org/docs/tech:osc
    // if you need further explanation of the LED methods below
    ledset	{ arg x, y, state;
        if ((state == 0) or: (state == 1)) {
            oscout.sendMsg(prefix++"/grid/led/set", x, y, state);
        } {
            "invalid argument (state must be 0 or 1).".warn;
        };
    }

    intensity	{ arg val;
        oscout.sendMsg(prefix++"/grid/led/intensity", val);
    }

	tilt_enable { arg device, state;
		oscout.sendMsg(prefix++"/tilt/set", device, state);
    }

	deviceList {
        portlst.clear; rows.clear; columns.clear;
        seroscnet.sendMsg("/serialosc/list", "127.0.0.1", NetAddr.localAddr.port);
    }

    cleanup {
        this.all(0);
        discovery.free;
		redrawTimer.do({arg dvc;
			redrawTimer[dvc].stop;
		});
        oscout.disconnect;
        seroscnet.disconnect;
    }

}