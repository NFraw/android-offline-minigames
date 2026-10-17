from pathlib import Path
import sys,importlib.util,subprocess,json,urllib.request,base64,math,wave,struct,shutil
ROOT=Path(__file__).resolve().parents[2]
BACKUP=ROOT/'assets-library/audio/kenney-sfx-backup'
RAW=ROOT/'app/src/main/res/raw'
EXTRA=ROOT/'assets-library/audio/added-sfx'
EXTRA.mkdir(parents=True,exist_ok=True)
for source in (BACKUP/'wav').glob('*.wav'): shutil.copy2(source,RAW/source.name)
repo_path='kenney_casinoaudio/Audio/diceThrow1.ogg'
ogg=EXTRA/'diceThrow1.ogg'
if not ogg.exists():
    request=urllib.request.Request('https://raw.githubusercontent.com/ETdoFresh/kenney.nl/master/'+repo_path,headers={'User-Agent':'MiniGames-local'})
    try: ogg.write_bytes(urllib.request.urlopen(request,timeout=20).read())
    except Exception:
        request=urllib.request.Request('https://api.github.com/repos/ETdoFresh/kenney.nl/contents/'+repo_path,headers={'User-Agent':'MiniGames-local'})
        data=json.load(urllib.request.urlopen(request,timeout=20));ogg.write_bytes(base64.b64decode(data['content']))
jobs=[{'in':str(ogg),'out':str(EXTRA/'dice.f32'),'meta':str(EXTRA/'dice.json')}]
(EXTRA/'decode-jobs.json').write_text(json.dumps(jobs),encoding='utf-8')
subprocess.run(['node',str(BACKUP/'tools/sfx/decode_batch.mjs'),str(EXTRA/'decode-jobs.json')],check=True,capture_output=True)
sys.path.insert(0,str(BACKUP/'tools/sfx'))
spec=importlib.util.spec_from_file_location('sound_dsp',BACKUP/'tools/sfx/build_sfx.py')
dsp=importlib.util.module_from_spec(spec);spec.loader.exec_module(dsp)
meta=json.loads((EXTRA/'dice.json').read_text())
samples=dsp.read_f32(str(EXTRA/'dice.f32'),meta['channels'])
samples=dsp.resample(samples,meta['sampleRate'],22050)
samples=dsp.trim_silence(dsp.remove_dc(samples),22050)[:int(0.85*22050)]
dsp.write_wav(str(RAW/'dice_roll.wav'),dsp.apply_fades(dsp.normalize(samples,0.62),22050),22050)
# 原创短琴音：一份C4采样通过SoundPool播放速率得到八个音高，无额外乐器库。
samples=[]
for i in range(int(0.34*22050)):
    t=i/22050
    sample=sum(math.sin(2*math.pi*261.6256*k*t)*math.exp(-(6+k*1.8)*t)/(k*k*0.8) for k in range(1,7))
    samples.append(sample*(1-math.exp(-t*450)))
dsp.write_wav(str(RAW/'key_note.wav'),dsp.apply_fades(dsp.normalize(samples,0.50),22050),22050)
(EXTRA/'README.md').write_text('音效补充：diceThrow1.ogg来自Kenney Casino Audio（CC0），官方 https://kenney.nl/assets/casino-audio ；下载镜像 '+repo_path+'。dice_roll.wav由该源转为16bit/22050Hz/单声道。key_note.wav为本项目原创加法合成C4短琴音，复用一个采样播放多个音高。已有26音效来源保留在kenney-sfx-backup/tools/sfx/README.md。所有短音效受设置控制，最多4路并发，同音限频，源OGG/解码中间文件不打包。',encoding='utf-8')
print('Audio ready: 26 restored + dice_roll + key_note')
