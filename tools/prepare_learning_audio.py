"""Normalize retained CC BY 3.0 piano recordings to smooth 44.1 kHz PCM clips."""
from pathlib import Path
import wave, json
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
reports=[]
for key,name in zip(['C4','D4','E4','F4','G4','A4','B4'],['do','re','mi','fa','sol','la','si']):
    with wave.open(str(ROOT/f'tools/learning-sources/piano-{key}.wav'),'rb') as f:
        rate=f.getframerate(); channels=f.getnchannels(); width=f.getsampwidth()
        raw=f.readframes(f.getnframes())
    assert width==2,(key,width)
    data=np.frombuffer(raw,dtype='<i2').astype(np.float64).reshape(-1,channels)/32768
    data=data.mean(axis=1)
    # Remove only leading digital silence. Preserve the acoustic attack and its timbre.
    audible=np.flatnonzero(np.abs(data)>0.002)
    if len(audible): data=data[max(0,int(audible[0])-int(rate*0.004)):]
    target_rate=44100; duration=0.9
    target=np.arange(int(target_rate*duration))/target_rate
    data=np.interp(target,np.arange(len(data))/rate,data)
    data-=data.mean()
    rms=np.sqrt(np.mean(data[int(target_rate*.03):int(target_rate*.5)]**2))
    gain=min(0.14/max(rms,1e-6),0.88/max(np.max(np.abs(data)),1e-6))
    data*=gain
    data[:132]*=np.linspace(0,1,132)
    tail=int(target_rate*.22); data[-tail:]*=np.linspace(1,0,tail)**1.5
    path=ROOT/f'godot/games/education/assets/note_{name}.wav'
    with wave.open(str(path),'wb') as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(target_rate)
        f.writeframes((np.clip(data,-1,1)*32767).astype('<i2').tobytes())
    reports.append(dict(note=name,key=key,rate=target_rate,duration=duration,peak=float(np.max(np.abs(data))),rms=float(np.sqrt(np.mean(data**2)))))
(ROOT/'tools/learning-sources/piano-quality.json').write_text(json.dumps(reports,indent=2))
print(json.dumps(reports))
