CVCenterKeyboard is a [SuperCollider](https://github.com/supercollider/supercollider) extension to the [CVCenter](https://github.com/nuss/CVCenter) library that models one or more MIDI keyboard(s). It allows playing one or more different [SynthDefs](https://pustota.basislager.org/_/sc-help/Help/Classes/SynthDef.html) in one keyboard definition. 

## Example
```supercollider
// define SynthDef with a gated envelope
(
SynthDef(\multi, {
    var env = EnvGen.ar(
        Env.adsr(\atk.kr(0.01), \dec.kr(0.3), \sust.kr(0.5), \rel.kr(0.7)),
        \gate.kr(1), // we need a gated envelope!
        doneAction: Done.freeSelf // free the Synth when done
    );
    var freq = \freq.kr(220);
    var son = [SinOsc.ar(freq), Saw.ar(freq), Pulse.ar(freq)];
    var which = SelectX.ar(\which.kr(0!2) * son.size, son); // stereo select
    Out.ar(\out.kr(0), which * \veloc.kr(1) * \amp.kr(0.5) * env);
}).add
)

// a few specs for the controls
(
Spec.add(\atk, #[0.01, 1.0], \exp);
Spec.add(\dec, #[0.1, 2.0]);
Spec.add(\sust, #[0.1, 1.0]);
Spec.add(\rel, #[0.1, 2.0, \exp]);
Spec.add(\which, #[0, 2]);
)

// create new keyboard, using pre-defined SynthDef
// will initialize MIDI and open CVCenter
~kb = CVCenterKeyboard.newSynthDef(\multi, \myKeyboard);

// set up controls
// will open a dialog
(
~kb.setUpControls(
    \multi, 
    \kb,
    \freq,
    \veloc,
    \freq,
    \out,
    setSynthDef: true
)
)

// after confirmation play the keyboard...
```
For detailed information look at the [helpfile](https://pustota.basislager.org/_/sc-help/Help/Classes/CVCenterKeyboard.html)

## Installation
Prerequesits:
- [SuperCollider](https://github.com/supercollider/supercollider/releases) installed on your machine
- [CVCenter](https://github.com/nuss/CVCenter) - either installed as Quark from within SuperCollider or download/clone the repository to your SuperCollider user extensions folder. Note the dependencies within CVCenter!

Finding the extensions folder on your platform can be accomplished executing the following sequence in the SuperCollider IDE:
```supercollider
Platform.userExtensionDir
```

**CVCenterKeyboard** can be installed by executing
```supercollider
Quarks.install("https://github.com/nuss/CVCenterKeyboard)
```
from within SuperCollider if you have set up Git on your computer. If not, download the ZIP archive and unzip it to your SuperCollider user extensions folder.

After having copied all files recompile the SuperCollider class library to make CVCenterKeyboard (and all othe classes) being recognized and usable.