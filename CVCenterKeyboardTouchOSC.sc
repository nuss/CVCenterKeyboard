CVCenterKeyboardTouchOSC {
	classvar <all, <>trackNums = #[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24];
	classvar <>seqNameCmds, <>seqAmpCmds, <>seqPauseResumeCmds, <>seqRemoveCmds, <>seqRemoveAllCmd = "/seq_remove_all";
	var <keyboard, <addr;
	var seqNameCmds, seqAmpCmds, seqPauseResumeCmds, seqRemoveCmds, seqRemoveAllCmd;

	*initClass {
		all = ();
		this.seqNameCmds = this.trackNums.collect { |i| "/seq_%_name".format(i) };
		this.seqAmpCmds = this.trackNums.collect { |i| "/seq_%_amp".format(i) };
		this.seqPauseResumeCmds = this.trackNums.collect { |i| "/seq_%_pause_resume".format(i) };
		this.seqRemoveCmds = this.trackNums.collect { |i| "/seq_%_remove".format(i) };
	}

	*new { |keyboard, addr|
		^super.newCopyArgs(keyboard, addr).init;
	}

	init {
		all.put(keyboard.keyboardDefName, this);
		"\nNew TouchOSC instance added: '%', NetAddr: %\n".format(keyboard.keyboardDefName, addr).inform;
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