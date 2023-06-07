TouchOSC {
	classvar <all;
	classvar <>seqNameCmds, <>seqAmpCmds, <>seqPauseResumeCmds, <>seqRemoveCmds, <>seqRemoveAllCmd;
	var <name, <addr;
	var <>debug = false;

	*initClass {
		all = ();
		this.seqNameCmds = (1..24).collect { |i| "/seq_%_name".format(i) };
		this.seqAmpCmds = (1..24).collect { |i| "/seq_%_amp".format(1) };
		this.seqPauseResumeCmds = (1..24).collect { |i| "/seq_%_pause_resume".format(i) };
		this.seqRemoveCmds = (1..24).collect { |i| "/seq_%_remove".format(i) };
		this.seqRemoveAllCmd = "/seq_remove_all";
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