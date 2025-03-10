/*
v.1.8
for information about monome devices:
https://monome.org

for further explanation of serialosc programming:
https://monome.org/docs/serialosc/osc/

written by:
raja das, ezra buchla, dani derks

*/

Monome {

	classvar seroscnet, discovery,
	prefixHandler,
	add, addCallback, addCallbackComplete,
	remove, removeCallback, removeCallbackComplete,
	rows, columns,
	portlst, prefixes, registeredDevices, deviceTypes,
	quadDirty, ledQuads, redrawTimers;

	var prefixID, rot, fpsVal; // instance variables

	*initClass {

		addCallback = nil;
		removeCallback = nil;
		portlst = List.new(0);
		registeredDevices = List.new(0);
		deviceTypes = List.new(0);
		addCallbackComplete = List.new(0);
		removeCallbackComplete = List.new(0);
		rows = List.new(0);
		columns = List.new(0);
		prefixes = List.new(0);
		seroscnet = NetAddr.new("localhost", 12002);
		seroscnet.sendMsg("/serialosc/list", "127.0.0.1", NetAddr.localAddr.port);
		seroscnet.sendMsg("/serialosc/notify", "127.0.0.1", NetAddr.localAddr.port);

		quadDirty = Dictionary.new;
		ledQuads = Dictionary.new;
		redrawTimers = Dictionary.new;

		this.buildOSCResponders;

		ServerQuit.add({
			seroscnet.disconnect;
			add.free;
			discovery.free;
			remove.free;
			redrawTimers.do({arg dvc;
				redrawTimers[dvc].stop;
			});
		},\default);

	}

	/*	*new { arg rotation, prefix, fps;
	^ super.new.init(prefix, rotation, fps);
	}*/
	*new {
		^ super.new.init();
	}

	*buildOSCResponders {

		discovery = OSCdef.newMatching(\monomediscover,
			{|msg, time, addr, recvPort|

				var portIDX, name, sizeDiscover, rw, cl, portID, serial, model, prefix, isArc;

				sizeDiscover = [0,0];
				serial = msg[1];
				model = msg[2];
				portID = msg[3];

				name = msg[2].asString.replace("monome","").replace("40h",64).asInteger;
				isArc = msg[2].asString.contains("arc");

				sizeDiscover = case
				{name == 64 } { [8,8] }
				{name == 128 } { [16,8] }
				{name == 256 } { [16,16] }
				{name == 512 } { [32,16] }
				{msg[2].asString.replace("monome ","") == "one" } { [8,8] }
				{msg[2].asString.replace("monome ","") == "zero" } { [16,16] }
				{msg[2].asString.contains("arc") } { [0,0] };

				if( portlst.includes(portID) == false, {
					portlst.add(portID);
					registeredDevices.add(serial);
					prefixes.add(prefix);
					addCallbackComplete.add(false);
					removeCallbackComplete.add(false);
					("monome device connected!").postln;
					(Char.tab ++ "model: " ++ model).postln;
					(Char.tab ++ "port: " ++ portID).postln;
					(Char.tab ++ "serial: " ++ serial).postln;
					portIDX = portlst.detectIndex({arg item, i; item == portID});
					columns.add(sizeDiscover[0]);
					rows.add(sizeDiscover[1]);
					if( isArc == true,
						{ deviceTypes.add("arc") },
						{ deviceTypes.add("grid") }
					);
					addCallback.value(serial,portID,prefixes[portIDX]);
				},{
					// ("device already registered!!!").postln;
				});
				seroscnet.sendMsg("/serialosc/notify", "127.0.0.1", NetAddr.localAddr.port);

		}, '/serialosc/device', seroscnet);

		add = OSCdef.newMatching(\monomeadd,
			{|msg, time, addr, recvPort|

				var portIDX, name, sizeDiscover, rw, cl, portID, serial, model, prefix;

				serial = msg[1];
				model = msg[2];
				portID = msg[3];

				name = msg[2].asString.replace("monome","").replace("40h",64).asInteger;

				sizeDiscover = case
				{name == 64 } { [8,8] }
				{name == 128 } { [16,8] }
				{name == 256 } { [16,16] }
				{name == 512 } { [32,16] }
				{msg[2].asString.replace("monome ","") == "one" } { [8,8] }
				{msg[2].asString.replace("monome ","") == "zero" } { [16,16] }
				{msg[2].asString.contains("arc") } { [0,0] };


				if( portlst.includes(portID) == false, {
					columns.add(sizeDiscover[0]);
					rows.add(sizeDiscover[1]);
					portlst.add(portID);
					prefixes.add(prefix);
					registeredDevices.add(serial);
					addCallbackComplete.add(false);
					removeCallbackComplete.add(false);
				});
				portIDX = portlst.detectIndex({arg item, i; item == portID});
				if( addCallbackComplete[portIDX] == false,{
					("monome device added!").postln;
					(Char.tab ++ "model: " ++ model).postln;
					(Char.tab ++ "port: " ++ portID).postln;
					(Char.tab ++ "serial: " ++ serial).postln;
					addCallback.value(serial,portID,prefixes[portIDX]);
					addCallbackComplete[portIDX] = true;
					removeCallbackComplete[portIDX] = false;
				});

				seroscnet.sendMsg("/serialosc/notify", "127.0.0.1", NetAddr.localAddr.port);

		}, '/serialosc/add', seroscnet);

		remove = OSCdef.newMatching(\monomeremove,
			{|msg, time, addr, recvPort|
				var portIDX;

				portIDX = portlst.detectIndex({arg item, i; item == msg[3]});
				if( portIDX.notNil, {
					if( removeCallbackComplete[portIDX] == false, {
						removeCallback.value(msg[2],msg[1],msg[3],prefixes[portIDX]);
						("monome device removed!").postln;
						(Char.tab ++ "model: " ++ msg[2]).postln;
						(Char.tab ++ "port: " ++ msg[3]).postln;
						(Char.tab ++ "serial: " ++ msg[1]).postln;
						// we don't want to remove devices from these lists:
						// registeredDevices.remove(msg[1]);
						// portlst.remove(msg[3]);
						addCallbackComplete[portIDX] = false;
						removeCallbackComplete[portIDX] = true;
					});
				});

				seroscnet.sendMsg("/serialosc/notify", "127.0.0.1", NetAddr.localAddr.port);

		}, '/serialosc/remove', seroscnet);

	}

	*refreshConnections {
		portlst.clear; registeredDevices.clear; prefixes.clear; rows.clear; columns.clear;
		seroscnet.sendMsg("/serialosc/list", "127.0.0.1", NetAddr.localAddr.port);
	}

	*getRegisteredDevices {
		^registeredDevices;
	}

	*getPortList {
		^portlst;
	}

	*getPrefixes {
		^prefixes;
	}

	*setAddCallback { arg func;
		addCallback = nil;
		addCallback = func;
	}

	*setRemoveCallback { arg func;
		removeCallback = nil;
		removeCallback = func;
	}

	init {
	}

}