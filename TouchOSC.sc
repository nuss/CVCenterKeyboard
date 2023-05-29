TouchOSC {
	classvar <all;
	var <name, <addr;
	var <>debug = false;

	*initClass {
		all = ();
	}

	*new { |name, addr|
		name = name.asSymbol;
		^super.newCopyArgs(name, addr).init;
	}

	init {
		all.put(name, this);
		"\nNew TouchOSC instance added: '%', NetAddr: %\n".format(name, addr).inform;
	}
}