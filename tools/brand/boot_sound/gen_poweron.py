#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Lumen power-on sound variants A-F.

Everything is synthesised here (additive / FM tones, formant choir, STFT-shaped
noise, reverb from a synthetic impulse response). No samples, no third-party audio.
Usage: python3 gen.py OUTDIR [A,B,...]
"""
import json
import os
import sys

import numpy as np
import pyloudnorm as pyln
from scipy import signal
from scipy.io import wavfile
from scipy.ndimage import minimum_filter1d, uniform_filter1d

SR = 48000
TARGET_LUFS = -18.0
CEIL_DBTP = -3.0
FADE_OUT = 0.6
TWO_PI = 2 * np.pi
PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


# ---------------------------------------------------------------- basics
def hz(name):
    pc, i = PC[name[0]], 1
    while name[i] in "#b":
        pc += 1 if name[i] == "#" else -1
        i += 1
    return 440.0 * 2 ** ((12 * (int(name[i:]) + 1) + pc - 69) / 12)


def cents(c):
    return 2 ** (c / 1200)


def tvec(dur):
    return np.arange(int(round(dur * SR))) / SR


def filt(x, kind, f, order=2):
    return signal.sosfilt(signal.butter(order, f, kind, fs=SR, output="sos"), x, axis=-1)


def ramp(x):
    """0..1 -> raised cosine 0..1, clipped."""
    return 0.5 - 0.5 * np.cos(np.pi * np.clip(x, 0, 1))


def ahr(t, att, hold, rel):
    return ramp(t / att) * (1 - ramp((t - att - hold) / rel))


def perc(t, att, tau):
    return ramp(t / att) * np.exp(-np.maximum(t - att, 0) / tau)


def panlaw(p):
    th = (p + 1) * np.pi / 4
    return np.cos(th) * np.sqrt(2), np.sin(th) * np.sqrt(2)


def pan_st(st, p):
    gl, gr = panlaw(p)
    return np.stack([st[0] * gl, st[1] * gr])


def place(dst, sig, t0, gain=1.0):
    k = int(round(t0 * SR))
    m = min(sig.shape[-1], dst.shape[-1] - k)
    if m > 0:
        dst[..., k:k + m] += gain * sig[..., :m]


def delay(x, sec):
    k = int(round(sec * SR))
    return np.concatenate([np.zeros(x.shape[:-1] + (k,)), x[..., :x.shape[-1] - k]], axis=-1)


# ---------------------------------------------------------------- voices
def partials(t, f, spec, att):
    out = np.zeros_like(t)
    for ratio, amp, tau in spec:
        if f * ratio < 8500:
            out += amp * np.exp(-t / tau) * np.sin(TWO_PI * f * ratio * t + 0.37 * ratio)
    return out * ramp(t / att)


def soft_bell(t, f, tau=1.3, att=0.008):
    """Mallet bell with a hum partial an octave down for warmth."""
    spec = [(0.5, 0.12, tau * 1.3), (1.0, 1.0, tau), (2.0, 0.32, tau * 0.45),
            (3.0, 0.12, tau * 0.28), (4.17, 0.07, tau * 0.16), (5.43, 0.035, tau * 0.1)]
    return partials(t, f, spec, att)


def glass(t, f, tau=1.2, att=0.005):
    spec = [(1.0, 1.0, tau), (2.0, 0.30, tau * 0.4), (3.0, 0.12, tau * 0.22),
            (4.23, 0.07, tau * 0.12), (6.1, 0.025, tau * 0.07)]
    return partials(t, f, spec, att)


def fm_tone(t, f, ratio=1.0, index=1.2, idx_tau=0.1, tau=0.6, att=0.006):
    mod = index * np.exp(-t / idx_tau) * np.sin(TWO_PI * f * ratio * t)
    return np.sin(TWO_PI * f * t + mod) * perc(t, att, tau)


def pad_note(t, f, env, bright, rng, voices=((-7, -0.6), (0, 0.0), (7, 0.6)), nmax=24):
    """Detuned band-limited saw voices with time-varying brightness. Returns (2, n)."""
    out = np.zeros((2, t.size))
    for c, pan in voices:
        fv = f * cents(c + rng.uniform(-1.5, 1.5))
        x = np.zeros_like(t)
        for n in range(1, int(min(nmax, 6500 // fv)) + 1):
            w = np.exp(-(n - 1) / bright) / n
            x += w * np.sin(TWO_PI * fv * n * t + rng.uniform(0, TWO_PI))
        gl, gr = panlaw(pan)
        out += np.stack([gl * x, gr * x])
    return out * env / len(voices)


def warm_sub(t, f, env, h2=0.45, h3=0.22, h4=0.08):
    """Sub sine plus explicit harmonics so small speakers still hear the pitch."""
    w = TWO_PI * f * t
    return env * (np.sin(w) + h2 * np.sin(2 * w + 0.3) + h3 * np.sin(3 * w + 0.7) + h4 * np.sin(4 * w + 1.1))


def shaped_noise(n, fc, bw_oct, amp, rng, nfft=2048, hop=256):
    """White noise shaped in the STFT domain by a moving log-gaussian band."""
    x = rng.standard_normal(n + nfft)
    f, tt, z = signal.stft(x, SR, nperseg=nfft, noverlap=nfft - hop)
    lf = np.log2(np.maximum(f, 10.0))[:, None]
    m = np.exp(-0.5 * ((lf - np.log2(fc(tt))[None, :]) / bw_oct(tt)[None, :]) ** 2) * amp(tt)[None, :]
    _, y = signal.istft(z * m, SR, nperseg=nfft, noverlap=nfft - hop)
    return y[:n]


def const(v):
    return lambda tt: v + 0 * tt


# ---------------------------------------------------------------- space
def mod_delay(x, base_ms, depth_ms, rate, phase):
    idx = np.arange(x.shape[-1])
    d = (base_ms + depth_ms * np.sin(TWO_PI * rate * idx / SR + phase)) * SR / 1000
    return np.interp(idx - d, idx, x, left=0.0, right=0.0)


def chorus_st(st, mix, rng, base=13.0, depth=2.5):
    r1, r2 = rng.uniform(0.17, 0.33, 2)
    p = rng.uniform(0, TWO_PI)
    left = st[0] + mix * mod_delay(st[0], base, depth, r1, p)
    right = st[1] + mix * mod_delay(st[1], base + 2.5, depth, r2, p + np.pi / 2)
    return np.stack([left, right]) / (1 + mix)


def haas(mono, ms, lead="L", level=0.85):
    d = delay(mono, ms / 1000) * level
    return np.stack([mono, d]) if lead == "L" else np.stack([d, mono])


def pingpong(st, dt, fb, reps, lp=3200.0):
    out = np.zeros_like(st)
    cur = st.mean(axis=0)
    for k in range(1, reps + 1):
        cur = filt(cur, "low", lp) * fb
        out[k % 2] += delay(cur, dt * k)
    return out


def make_ir(rt60, rng, predelay, er):
    n = int(SR * min(rt60 * 1.15, 3.2))
    t = np.arange(n) / SR
    nz = rng.standard_normal(n)
    late = (filt(nz, "low", 400) * np.exp(-6.91 * t / (rt60 * 1.15))
            + filt(nz, "band", [400, 2500]) * np.exp(-6.91 * t / rt60)
            + 0.5 * filt(nz, "high", 2500) * np.exp(-6.91 * t / (rt60 * 0.4)))
    late *= 1 - np.exp(-t / 0.03)
    late /= np.sqrt(np.sum(late ** 2))
    pd = int(predelay * SR)
    ir = np.zeros(n + pd)
    ir[pd:] = late
    for k, tt in enumerate(np.sort(rng.uniform(0.005, 0.05, 10))):
        ir[int(tt * SR)] += er * 0.3 * 0.8 ** k
    return ir


def reverb(bus, rt60, seed, predelay=0.02, er=0.6):
    rng = np.random.default_rng(seed)
    x = filt(filt(bus, "high", 160), "low", 6500)
    h = [make_ir(rt60, rng, predelay, er) for _ in range(4)]
    n = bus.shape[1]

    def cv(a, ir):
        return signal.fftconvolve(a, ir)[:n]

    return np.stack([cv(x[0], h[0]) + 0.45 * cv(x[1], h[1]), cv(x[1], h[2]) + 0.45 * cv(x[0], h[3])])


class Mix:
    def __init__(self, dur):
        self.n = int(round(dur * SR))
        self.dry = np.zeros((2, self.n))
        self.bus = np.zeros((2, self.n))

    def add(self, sig, t0=0.0, pan=0.0, gain=1.0, rev=0.3):
        if sig.ndim == 1:
            gl, gr = panlaw(pan)
            sig = np.stack([gl * sig, gr * sig])
        place(self.dry, sig, t0, gain)
        place(self.bus, sig, t0, gain * rev)

    def render(self, rt60, wet, seed, predelay=0.02, er=0.6):
        return self.dry + wet * reverb(self.bus, rt60, seed, predelay, er)


# ---------------------------------------------------------------- variants
def var_a():
    """Airy pad swell + soft bell chord, D major 9."""
    rng = np.random.default_rng(101)
    dur = 3.3
    m, t = Mix(dur), tvec(dur)
    env = ahr(t, 0.95, 0.75, 1.3)
    bright = 1.2 + 3.3 * ramp(t / 1.0) - 1.5 * ramp((t - 1.2) / 1.5)
    for name, pan, g in [("D3", 0.0, 1.0), ("A3", -0.45, 0.8), ("C#4", 0.45, 0.6), ("E4", -0.7, 0.5), ("F#4", 0.7, 0.5)]:
        st = pan_st(pad_note(t, hz(name), env, bright, rng), pan)
        m.add(chorus_st(st, 0.5, rng), 0, gain=0.16 * g, rev=0.45)
    m.add(warm_sub(t, hz("D2"), ahr(t, 0.7, 0.9, 1.2)), 0, gain=0.12, rev=0.03)
    tb = tvec(2.7)
    for k, (name, pan) in enumerate([("D4", -0.5), ("F#4", -0.2), ("A4", 0.1), ("C#5", 0.4), ("E5", 0.65)]):
        m.add(soft_bell(tb, hz(name), tau=1.0, att=0.009), 0.55 + 0.045 * k, pan=pan, gain=0.12 * (1 - 0.08 * k), rev=0.6)
    for ch in (0, 1):
        st = np.zeros((2, m.n))
        st[ch] = shaped_noise(m.n, const(3000.0), const(0.6), lambda tt: ahr(tt, 0.8, 0.4, 0.9), rng)
        m.add(st, 0, gain=0.03, rev=0.3)
    return m.render(rt60=2.2, wet=0.35, seed=11)


def var_b():
    """Rising shimmer arpeggio resolving to an open fifth, A."""
    rng = np.random.default_rng(202)
    dur = 3.0
    m = Mix(dur)
    arp = np.zeros((2, m.n))
    names = ["A3", "E4", "A4", "B4", "C#5", "E5", "A5"]
    pans = np.linspace(-0.7, 0.7, len(names))
    for k, name in enumerate(names):
        tn = tvec(1.6)
        f = hz(name)
        x = fm_tone(tn, f, 1.0, 1.8, 0.12, 0.55, 0.006)
        tw = fm_tone(tn, f * cents(5), 1.0, 1.0, 0.09, 0.5, 0.008)
        gl, gr = panlaw(pans[k])
        hl, hr = panlaw(-pans[k] * 0.6)
        st = np.stack([gl * x + 0.4 * hl * tw, gr * x + 0.4 * hr * tw])
        place(arp, st, 0.04 + 0.072 * k, 0.75 + 0.05 * k)
    m.add(arp, gain=0.11, rev=0.5)
    m.add(pingpong(arp, 0.16, 0.38, 4), gain=0.09, rev=0.6)
    tr = tvec(dur - 0.5)
    env = ahr(tr, 0.4, 0.75, 1.1)
    bright = 1.5 + 1.8 * ramp(tr / 0.5)
    for name, pan, g in [("A2", 0.0, 1.0), ("E3", -0.4, 0.8), ("A3", 0.4, 0.65), ("E4", 0.0, 0.45)]:
        st = pan_st(pad_note(tr, hz(name), env, bright, rng), pan)
        m.add(chorus_st(st, 0.5, rng), 0.5, gain=0.15 * g, rev=0.45)
    m.add(warm_sub(tr, hz("A1"), ahr(tr, 0.35, 0.7, 1.0)), 0.5, gain=0.08, rev=0.0)
    th = tvec(2.4)
    for name, pan in [("A5", -0.75), ("E6", 0.75), ("A5", 0.3), ("E6", -0.3)]:
        f = hz(name) * cents(rng.uniform(-4, 4))
        x = np.sin(TWO_PI * f * th + rng.uniform(0, TWO_PI)) * ahr(th, 0.5, 0.6, 1.1)
        x *= 1 + 0.15 * np.sin(TWO_PI * 0.7 * th + rng.uniform(0, TWO_PI))
        m.add(x, 0.35, pan=pan, gain=0.03, rev=0.9)
    return m.render(rt60=2.4, wet=0.4, seed=22)


def var_c():
    """Deep cinema whoosh landing on a low chime, G."""
    rng = np.random.default_rng(303)
    dur, hit = 3.4, 1.0
    m, t = Mix(dur), tvec(dur)

    def fc(tt):
        rise = 140 * (1800 / 140) ** (np.clip(tt / hit, 0, 1) ** 1.3)
        return np.where(tt < hit, rise, 1800 * 0.25 ** ramp((tt - hit) / 0.4))

    def amp(tt):
        return np.where(tt < hit, ramp(tt / hit) ** 1.6, np.exp(-(tt - hit) / 0.12))

    def bw(tt):
        return 1.1 - 0.3 * np.clip(tt / hit, 0, 1)

    common = shaped_noise(m.n, fc, bw, amp, rng)
    nl, nr = shaped_noise(m.n, fc, bw, amp, rng), shaped_noise(m.n, fc, bw, amp, rng)
    gl, gr = panlaw(-0.6 + 1.2 * ramp(t / hit))
    m.add(np.stack([gl * (0.6 * nl + 0.5 * common), gr * (0.6 * nr + 0.5 * common)]), gain=0.09, rev=0.5)
    rum = shaped_noise(m.n, const(70.0), const(0.7),
                       lambda tt: np.where(tt < hit, ramp(tt / hit) ** 2, np.exp(-(tt - hit) / 0.3)), rng)
    m.add(rum, gain=0.05, rev=0.1)
    ph = TWO_PI * np.cumsum(hz("G1") * (0.94 + 0.06 * ramp(t / hit))) / SR
    sub_env = np.where(t < hit, ramp(t / hit) ** 2, np.exp(-(t - hit) / 0.9))
    m.add(sub_env * (np.sin(ph) + 0.5 * np.sin(2 * ph + 0.3) + 0.28 * np.sin(3 * ph + 0.6) + 0.12 * np.sin(4 * ph + 1.0)),
          gain=0.14, rev=0.03)
    tc = tvec(dur - hit)
    for name, dt, pan, g in [("G3", 0.0, -0.25, 1.0), ("D4", 0.012, 0.3, 0.6), ("G4", 0.022, 0.0, 0.42), ("D5", 0.03, -0.4, 0.18)]:
        m.add(soft_bell(tc, hz(name), tau=1.0, att=0.01), hit + dt, pan=pan, gain=0.2 * g, rev=0.6)
    m.add(warm_sub(tc, hz("G2"), perc(tc, 0.015, 0.7), 0.3, 0.1, 0.0), hit, gain=0.12, rev=0.1)
    return m.render(rt60=2.8, wet=0.45, seed=33, predelay=0.03)


def var_d():
    """Three-note glass pluck motif with a long echo tail, A."""
    rng = np.random.default_rng(404)
    dur = 3.4
    m = Mix(dur)
    plk = np.zeros((2, m.n))
    for name, t0, pan, g, tau in [("A4", 0.06, -0.45, 0.75, 1.0), ("E5", 0.30, 0.45, 0.9, 1.0), ("C#5", 0.54, 0.0, 1.0, 1.4)]:
        tn, f = tvec(dur - t0), hz(name)
        x = glass(tn, f, tau, 0.006) + 0.35 * glass(tn, f / 2, tau * 0.8, 0.008)
        tw = glass(tn, f * cents(4), tau * 0.9, 0.007)
        gl, gr = panlaw(pan)
        hl, hr = panlaw(-pan * 0.7)
        place(plk, np.stack([gl * x + 0.3 * hl * tw, gr * x + 0.3 * hr * tw]), t0, g)
    m.add(plk, gain=0.15, rev=0.55)
    m.add(pingpong(plk, 0.21, 0.42, 5, lp=2800), gain=0.13, rev=0.7)
    tb = tvec(dur - 0.45)
    env = ahr(tb, 0.6, 0.8, 1.3)
    for name, pan, g in [("A2", 0.0, 1.0), ("E3", -0.5, 0.7), ("A3", 0.5, 0.5)]:
        st = pan_st(pad_note(tb, hz(name), env, 1.4, rng), pan)
        m.add(chorus_st(st, 0.5, rng), 0.45, gain=0.09 * g, rev=0.5)
    m.add(warm_sub(tb, hz("A1"), ahr(tb, 0.5, 0.8, 1.1)), 0.45, gain=0.06, rev=0.0)
    return m.render(rt60=2.6, wet=0.45, seed=44)


VOWEL = {  # formant freqs, amps, bandwidths
    "u": (np.array([320, 800, 2500.]), np.array([1.0, 0.35, 0.10]), np.array([70, 90, 140.])),
    "a": (np.array([700, 1150, 2650.]), np.array([1.0, 0.55, 0.18]), np.array([90, 100, 150.])),
    "o": (np.array([480, 820, 2550.]), np.array([1.0, 0.45, 0.12]), np.array([80, 90, 140.])),
}


def singer(t, f, fscale, rng, hop=240):
    """One choir voice: harmonics weighted by a morphing vowel (u -> a -> o)."""
    ph = TWO_PI * np.cumsum(f) / SR
    tf, ff = t[::hop], f[::hop]
    w1 = 0.8 * ramp(tf / 1.3)
    w2 = ramp((tf - 1.8) / 1.0)
    mix = []
    for i in range(3):
        v = (1 - w1) * VOWEL["u"][i][:, None] + w1 * VOWEL["a"][i][:, None]
        v = (1 - w2) * v + w2 * VOWEL["o"][i][:, None]
        mix.append(v)
    fk, ak, bk = mix[0] * fscale, mix[1], mix[2]
    out = np.zeros_like(t)
    for h in range(1, int(5200 / f.max()) + 1):
        fr = h * ff
        g = (ak / (1 + ((fr[None, :] - fk) / (bk / 2)) ** 2)).sum(axis=0) + 0.02
        g *= h ** -0.6 + (0.6 if h == 1 else 0.25 if h == 2 else 0.0)
        out += np.interp(t, tf, g) * np.sin(h * ph + rng.uniform(0, TWO_PI))
    return out


def var_e():
    """Soft choir vowel pad, F sus4 -> F."""
    rng = np.random.default_rng(505)
    dur = 3.3
    m, t = Mix(dur), tvec(dur)
    choir = np.zeros((2, m.n))
    glide = ramp((t - 1.1) / 0.15)
    parts = [  # base freq fn, pans of singers, gain, formant scale, start, env
        (lambda: hz("F2") + 0 * t, [-0.1, 0.1, 0.0], 0.6, 1.0, 0.0),
        (lambda: hz("C4") + 0 * t, [-0.65, -0.45, -0.25], 0.55, 1.0, 0.0),
        (lambda: hz("F4") + 0 * t, [0.25, 0.45, 0.65], 0.5, 1.12, 0.0),
        (lambda: hz("Bb4") + (hz("A4") - hz("Bb4")) * glide, [-0.75, 0.0, 0.75], 0.45, 1.15, 0.0),
        (lambda: hz("C5") + 0 * t, [-0.4, 0.4], 0.22, 1.18, 0.9),
    ]
    for base, pans, g, fs, start in parts:
        f0 = base()
        for pan in pans:
            off = start + rng.uniform(0, 0.04)
            vib = 1 + 0.004 * ramp((t - off - 0.3) / 0.6) * np.sin(TWO_PI * rng.uniform(4.8, 5.6) * t + rng.uniform(0, TWO_PI))
            vib *= 1 + 0.0015 * np.sin(TWO_PI * 0.2 * t + rng.uniform(0, TWO_PI))
            x = singer(t, f0 * cents(rng.uniform(-4, 4)) * vib, fs, rng)
            env = ahr(t - off, 0.6, 1.3 - 0.5 * (start > 0), 1.1)
            gl, gr = panlaw(pan)
            choir += np.stack([gl * x, gr * x]) * env * g / len(pans)
    m.add(chorus_st(choir, 0.35, rng), gain=0.09, rev=0.5)
    m.add(warm_sub(t, hz("F2"), ahr(t, 0.6, 1.3, 1.1), 0.35, 0.12, 0.0), gain=0.012, rev=0.05)
    for ch in (0, 1):
        st = np.zeros((2, m.n))
        st[ch] = shaped_noise(m.n, const(1200.0), const(0.8), lambda tt: ahr(tt, 0.25, 0.1, 0.5), rng)
        m.add(st, gain=0.01, rev=0.4)
    return m.render(rt60=2.6, wet=0.42, seed=55)


def var_f():
    """Minimal two-tone 'light on' (E4 -> C#5) over a warm A sub."""
    rng = np.random.default_rng(606)
    dur = 2.4
    m = Mix(dur)

    def tone(tn, f, tau):
        mod = 0.6 * np.exp(-tn / 0.08) * np.sin(TWO_PI * f * tn)
        x = (np.sin(TWO_PI * f * tn + mod) + 0.16 * np.exp(-tn / (tau * 0.5)) * np.sin(2 * TWO_PI * f * tn + 0.4)
             + 0.10 * np.exp(-tn / (tau * 0.3)) * np.sin(3 * TWO_PI * f * tn + 0.9)
             + 0.06 * np.exp(-tn / (tau * 0.18)) * np.sin(4.02 * TWO_PI * f * tn + 1.3)
             + 0.035 * np.exp(-tn / (tau * 0.12)) * np.sin(5.03 * TWO_PI * f * tn + 2.1))
        return x * perc(tn, 0.012, tau)

    x1 = tone(tvec(dur - 0.05), hz("E4"), 0.75)
    m.add(haas(x1, 9.86, "L"), 0.05, gain=0.26, rev=0.4)  # 3.25 periods: quarter-cycle offset
    x2 = tone(tvec(dur - 0.30), hz("C#5"), 1.0)
    m.add(haas(x2, 8.57, "R"), 0.30, gain=0.24, rev=0.45)  # 4.75 periods
    tg = tvec(dur - 0.3)
    for name, pan in [("A4", -0.6), ("E5", 0.6)]:
        x = np.sin(TWO_PI * hz(name) * cents(rng.uniform(-3, 3)) * tg) * ahr(tg, 0.35, 0.25, 0.9)
        m.add(pan_st(chorus_st(np.stack([x, x]), 0.7, rng), pan), 0.3, gain=0.05, rev=0.6)
    ts = tvec(dur - 0.28)
    m.add(warm_sub(ts, hz("A1"), ahr(ts, 0.22, 0.35, 1.0), 0.6, 0.3, 0.1), 0.28, gain=0.09, rev=0.02)
    return m.render(rt60=1.6, wet=0.3, seed=66, er=1.0)


VARIANTS = [
    ("A", "air_pad_bell", "Воздух: пэд и мягкий колокольный аккорд", var_a),
    ("B", "shimmer_fifth", "Мерцание: восходящее арпеджио в квинту", var_b),
    ("C", "cinema_whoosh_chime", "Кино: глубокий взлёт и низкий колокол", var_c),
    ("D", "glass_pluck_motif", "Стекло: три стеклянные ноты с долгим эхом", var_d),
    ("E", "soft_choir", "Хор: мягкий вокальный пэд", var_e),
    ("F", "light_on_two_tone", "Свет: два тона и тёплый бас", var_f),
]


# ---------------------------------------------------------------- master + checks
def lufs(x):
    return pyln.Meter(SR).integrated_loudness(x.T)


def true_peak(x):
    return np.abs(signal.resample_poly(x, 4, 1, axis=-1)).max()


def limiter(x, ceil, look=0.004, rel=0.08):
    n = x.shape[1]
    up = np.abs(signal.resample_poly(x, 4, 1, axis=-1)).max(axis=0)[: n * 4]
    pk = up.reshape(n, 4).max(axis=1)
    need = np.minimum(1.0, ceil / np.maximum(pk, 1e-9))
    la = int(look * SR)
    g = uniform_filter1d(minimum_filter1d(need, 2 * la + 1), la)
    a, s = np.exp(-1 / (rel * SR)), 1.0
    out = np.empty_like(g)
    for i, v in enumerate(g):
        s = v if v < s else a * s + (1 - a) * v
        out[i] = s
    return x * out


def fades(n):
    t = np.arange(n) / SR
    return ramp(t / 0.02) * (1 - ramp((t - (t[-1] - FADE_OUT)) / FADE_OUT))


def finalize(st, target_corr=0.55):
    x = filt(filt(st, "high", 28), "low", 10000)
    mid, side = (x[0] + x[1]) / 2, filt((x[0] - x[1]) / 2, "high", 140)  # bass stays mono
    pm, ps, pms = np.mean(mid ** 2), np.mean(side ** 2), np.mean(mid * side)
    gs = np.linspace(0.6, 1.8, 241)
    corr = (pm - gs ** 2 * ps) / np.sqrt((pm + gs ** 2 * ps) ** 2 - 4 * gs ** 2 * pms ** 2)
    g = float(gs[np.argmin(np.abs(corr - target_corr))])
    x = np.stack([mid + g * side, mid - g * side]) * fades(x.shape[1])
    ceil = 10 ** (CEIL_DBTP / 20) * 0.96
    for _ in range(8):
        x *= 10 ** ((TARGET_LUFS - lufs(x)) / 20)
        if true_peak(x) <= ceil:
            break
        x = limiter(x, ceil * 0.97)
    return x, g


def write_wav(path, x, rng):
    tpdf = (rng.random(x.shape) - rng.random(x.shape)) / 32768 * fades(x.shape[1])
    y = np.clip(np.round((x + tpdf) * 32767), -32767, 32767).astype(np.int16)
    wavfile.write(path, SR, y.T)


def db(v):
    return float(20 * np.log10(max(v, 1e-12)))


def rms(a):
    return float(np.sqrt(np.mean(a ** 2)))


def analyze(path):
    sr, y = wavfile.read(path)
    yi = y.T.astype(np.int32)
    x = yi / 32768.0
    n = x.shape[1]
    meter = pyln.Meter(SR)
    il = meter.integrated_loudness(x.T)
    mom = []
    for s in range(0, n - int(0.4 * SR), int(0.1 * SR)):
        with np.errstate(divide="ignore"):
            v = meter.integrated_loudness(x[:, s:s + int(0.4 * SR)].T)
        if np.isfinite(v):
            mom.append(v)
    f, p = signal.welch(x.mean(0), SR, nperseg=8192)
    tot = p.sum()
    bands = {k: float(10 * np.log10(p[(f >= a) & (f < b)].sum() / tot + 1e-20)) for k, (a, b) in {
        "<60": (0, 60), "60-150": (60, 150), "150-500": (150, 500), "500-2k": (500, 2000),
        "2k-6k": (2000, 6000), "6k-10k": (6000, 10000), ">10k": (10000, 24001)}.items()}
    w = 2400
    prof = max(rms(x[:, s:s + w]) for s in range(0, n - w, 1200))
    fs = n - int(FADE_OUT * SR)
    r = {
        "file": os.path.basename(path), "seconds": round(n / sr, 3), "rate": sr, "channels": x.shape[0],
        "lufs_integrated": round(il, 2), "lufs_momentary_max": round(max(mom), 2),
        "sample_peak_dbfs": round(db(np.abs(x).max()), 2), "true_peak_dbtp": round(db(true_peak(x)), 2),
        "dc_dbfs": [round(db(abs(x[c].mean())), 1) for c in (0, 1)],
        "stereo_corr": round(float(np.corrcoef(x[0], x[1])[0, 1]), 3),
        "stereo_corr_above_200hz": round(float(np.corrcoef(*filt(x, "high", 200))[0, 1]), 3),
        "mono_sum_change_db": round(lufs(np.stack([x.mean(0)] * 2)) - il, 2),
        "small_speaker_change_db": round(lufs(filt(filt(x, "high", 180, 4), "low", 12000)) - il, 2),
        "first_10ms_dbfs": round(db(rms(x[:, :480])), 1), "last_50ms_dbfs": round(db(rms(x[:, -2400:])), 1),
        "edge_samples": [int(np.abs(yi[:, 0]).max()), int(np.abs(yi[:, -1]).max())],
        "clipped_samples": int((np.abs(yi) >= 32767).sum()),
        "level_at_fade_start_db": round(db(rms(x[:, fs - w:fs])) - db(prof), 1),
        "band_share_db": {k: round(v, 1) for k, v in bands.items()},
    }
    r["checks"] = {
        "length 2.0-3.5 s": 2.0 <= r["seconds"] <= 3.5,
        "stereo 48 kHz": sr == 48000 and x.shape[0] == 2,
        "no clipping": r["clipped_samples"] == 0,
        "true peak <= -2.8 dBTP": r["true_peak_dbtp"] <= -2.8,
        "loudness -18 +/-0.5 LUFS": abs(il - TARGET_LUFS) <= 0.5,
        "stereo corr 0.3-0.8": 0.3 <= r["stereo_corr"] <= 0.8,
        "no DC (< -60 dBFS)": max(r["dc_dbfs"]) < -60,
        "clean fade in": r["first_10ms_dbfs"] < -50 and r["edge_samples"][0] == 0,
        "clean fade out": r["last_50ms_dbfs"] < -60 and r["edge_samples"][1] == 0,
        "no harsh highs (>10 kHz < -35 dB)": bands[">10k"] < -35,
        "mono sum loss < 3 dB": r["mono_sum_change_db"] > -3,
        "small speaker loss < 4 dB": r["small_speaker_change_db"] > -4,
    }
    r["checks"] = {k: bool(v) for k, v in r["checks"].items()}
    r["all_ok"] = all(r["checks"].values())
    return r


def spectro(png, path, title):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    sr, y = wavfile.read(path)
    x = y.T / 32768.0
    t = np.arange(x.shape[1]) / sr
    fig = plt.figure(figsize=(11, 5.6), dpi=100)
    gs = fig.add_gridspec(2, 1, height_ratios=[1, 3], hspace=0.06)
    a0 = fig.add_subplot(gs[0])
    a0.plot(t, x[0], lw=0.35, color="#4C8BF5", alpha=0.85, label="L")
    a0.plot(t, x[1], lw=0.35, color="#F2B26B", alpha=0.7, label="R")
    for s in (1, -1):
        a0.axhline(s * 10 ** (-3 / 20), color="#999", lw=0.6, ls="--")
    a0.set_xlim(0, t[-1]); a0.set_ylim(-1, 1); a0.tick_params(labelbottom=False)
    a0.legend(loc="upper right", fontsize=7, frameon=False); a0.set_ylabel("ampl.")
    a1 = fig.add_subplot(gs[1], sharex=a0)
    f, tt, s = signal.spectrogram(x.mean(0), sr, window="hann", nperseg=4096, noverlap=4096 - 384)
    keep = (f >= 25) & (f <= 16000)
    sdb = 10 * np.log10(s[keep] + 1e-20)
    a1.pcolormesh(tt, f[keep], sdb, vmin=sdb.max() - 85, vmax=sdb.max(), cmap="magma", shading="auto", rasterized=True)
    a1.set_yscale("log"); a1.set_ylim(30, 16000); a1.set_facecolor("black")
    ticks = [50, 100, 200, 500, 1000, 2000, 5000, 10000]
    a1.set_yticks(ticks); a1.set_yticklabels(["50", "100", "200", "500", "1k", "2k", "5k", "10k"])
    a1.set_xlabel("time, s"); a1.set_ylabel("Hz (mid, L+R)")
    fig.suptitle(title, fontsize=11)
    fig.savefig(png, dpi=100, bbox_inches="tight")
    plt.close(fig)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "out"
    only = sys.argv[2].split(",") if len(sys.argv) > 2 else None
    os.makedirs(out, exist_ok=True)
    rng = np.random.default_rng(7)
    mpath = os.path.join(out, "metrics.json")
    results = json.load(open(mpath)) if os.path.exists(mpath) else {}
    for key, slug, ru, fn in VARIANTS:
        if only and key not in only:
            continue
        x, g = finalize(fn())
        base = os.path.join(out, f"lumen_poweron_{key}_{slug}")
        write_wav(base + ".wav", x, rng)
        r = analyze(base + ".wav")
        r["side_gain"] = g
        r["name"] = ru
        results[key] = r
        spectro(base + ".png", base + ".wav",
                f"{key}: {ru}  |  {r['seconds']} s, {r['lufs_integrated']} LUFS, "
                f"TP {r['true_peak_dbtp']} dBTP, corr {r['stereo_corr']}")
        bad = [k for k, v in r["checks"].items() if not v]
        print(f"{key} {r['seconds']}s I={r['lufs_integrated']} M={r['lufs_momentary_max']} SP={r['sample_peak_dbfs']} "
              f"TP={r['true_peak_dbtp']} corr={r['stereo_corr']}/{r['stereo_corr_above_200hz']} side_g={g:.2f} "
              f"mono={r['mono_sum_change_db']} small={r['small_speaker_change_db']} fadeStart={r['level_at_fade_start_db']} "
              f"bands={r['band_share_db']} {'OK' if not bad else 'FAIL ' + str(bad)}", flush=True)
    json.dump(results, open(mpath, "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
