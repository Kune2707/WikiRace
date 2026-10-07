export type Cue = "countdown" | "go" | "close" | "win" | "lose";
let context: AudioContext | undefined;
export function unlockSound() {
  try {
    context ??= new AudioContext();
    void context.resume().catch(() => {});
  } catch {
    /* Audio is optional. */
  }
}
export function playSound(cue: Cue) {
  if (!context || context.state !== "running") return;
  const notes = {
    countdown: [440],
    go: [660, 880],
    close: [520, 420],
    win: [523, 659, 784],
    lose: [392, 330, 262],
  }[cue];
  notes.forEach((frequency, index) => {
    const oscillator = context!.createOscillator();
    const gain = context!.createGain();
    const at = context!.currentTime + index * 0.12;
    oscillator.frequency.value = frequency;
    gain.gain.setValueAtTime(0, at);
    gain.gain.linearRampToValueAtTime(0.035, at + 0.015);
    gain.gain.exponentialRampToValueAtTime(0.001, at + 0.11);
    oscillator.connect(gain);
    gain.connect(context!.destination);
    oscillator.start(at);
    oscillator.stop(at + 0.12);
    oscillator.onended = () => {
      oscillator.disconnect();
      gain.disconnect();
    };
  });
}
