CVCenterKeyboardTouchOSC {
	classvar <all;
	classvar <>seqNameCmds, <>seqAmpCmds, <>seqPauseResumeCmds, <>seqRemoveCmds, <>seqRemoveAllCmd = "/seq_remove_all";
	var <name, <addr;
	var seqNameCmds, seqAmpCmds, seqPauseResumeCmds, seqRemoveCmds, seqRemoveAllCmd;
	var <>debug = false;

	*initClass {
		all = ();
		this.seqNameCmds = (1..24).collect { |i| "/seq_%_name".format(i) };
		this.seqAmpCmds = (1..24).collect { |i| "/seq_%_amp".format(i) };
		this.seqPauseResumeCmds = (1..24).collect { |i| "/seq_%_pause_resume".format(i) };
		this.seqRemoveCmds = (1..24).collect { |i| "/seq_%_remove".format(i) };
	}

	*new { |name, addr|
		name = name.asSymbol;
		^super.newCopyArgs(name, addr).init;
	}

	init {
		all.put(name, this);
		"\nNew TouchOSC instance added: '%', NetAddr: %\n".format(name, addr).inform;
	}

	seqNameCmds {
		^seqNameCmds ?? { this.class.seqNameCmds }
	}

	seqNameCmds_ { |cmdsList|
		seqNameCmds = cmdsList;
	}

	seqAmpCmds {
		^seqAmpCmds ?? { this.class.seqAmpCmds }
	}

	seqAmpCmds_ { |cmdsList|
		seqAmpCmds = cmdsList;
	}

	seqPauseResumeCmds {
		^seqPauseResumeCmds ?? { this.class.seqPauseResumeCmds }
	}

	seqPauseResumeCmds_ { |cmdsList|
		seqPauseResumeCmds = cmdsList;
	}

	seqRemoveCmds {
		^seqRemoveCmds ?? { this.class.seqRemoveCmds }
	}

	seqRemoveCmds_ { |cmdsList|
		seqRemoveCmds = cmdsList;
	}

	seqRemoveAllCmd {
		^seqRemoveAllCmd ?? { this.class.seqRemoveAllCmd }
	}

	seqRemoveAllCmd_ { |cmd|
		seqRemoveAllCmd = cmd;
	}
}