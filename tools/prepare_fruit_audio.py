"""Original offline layered foley synthesis; no original-game recordings used."""
from pathlib import Path
import wave
import numpy as np

RATE = 44100
OUT = Path(__file__).resolve().parents[1] / 'godot/games/fruit/assets/audio'
OUT.mkdir(parents=True, exist_ok=True)
rng = np.random.default_rng(20261016)

def noise(n, smoothing=1):
    x = rng.normal(size=n)
    return np.convolve(x, np.ones(smoothing)/smoothing, 'same')

def save(name, x, peak):
    x -= x.mean()
    x *= np.minimum(np.arange(len(x))/220, 1)
    x *= np.minimum(np.arange(len(x))[::-1]/660, 1)
    x = np.tanh(x*1.3)
    x *= peak/max(abs(x).max(), 1e-9)
    with wave.open(str(OUT/(name+'.wav')), 'wb') as f:
        f.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        f.writeframes((x*32767).astype('<i2').tobytes())

for i in range(3):
    t = np.arange(int(RATE*(.20+i*.025)))/RATE
    n = noise(len(t)); airy = n-np.convolve(n,np.ones(17)/17,'same')
    envelope = (t/.045)**2*np.exp(-t/(.022+i*.003))
    phase = 2*np.pi*(2600*t-3400*t*t)
    save('swipe_'+str(i), (airy*.62+np.sin(phase)*.035)*envelope, .48)

for i in range(4):
    t = np.arange(int(RATE*(.32+i*.025)))/RATE
    # Sharp skin tear, low wet body, irregular small juice pops.
    tear = (noise(len(t))-np.convolve(noise(len(t)),np.ones(11)/11,'same'))*np.exp(-t/ .020)*.34
    wet = noise(len(t),25)*np.exp(-t/.095)*2.5
    body = np.sin(2*np.pi*(165*t-130*t*t))*np.exp(-t/.032)*.4
    x = tear+wet+body
    for delay in [.032,.061,.098,.15,.205]:
        u=np.maximum(0,t-delay-rng.uniform(0,.012))
        pulse=np.sin(2*np.pi*(rng.uniform(650,1300)*u+1900*u*u))*np.exp(-u/.010)
        pulse[t<delay+.012]=0
        x+=pulse*.09
    save('slice_'+str(i),x,.72)

t=np.arange(int(RATE*1.45))/RATE
boom=np.sin(2*np.pi*(78*t-28*t*t))*np.exp(-t/ .25)*.72
blast=noise(len(t),4)*np.exp(-t/.14)*1.3
crack=noise(len(t))*np.exp(-t/.012)*.7
debris=noise(len(t),14)*np.exp(-t/.48)*.3
save('bomb',boom+blast+crack+debris,.88)
for p in OUT.glob('*.wav'):
    with wave.open(str(p)) as f: print(p.name, round(f.getnframes()/RATE,3), 'seconds')
