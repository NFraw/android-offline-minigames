"""Build offline educational banks from retained sources; run with bundled Python."""
import json, re, unicodedata
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'tools/learning-sources'
OUT = ROOT / 'godot/games/education'
WORDS = '''
apple 苹果;cat 猫;dog 狗;fish 鱼;sun 太阳;moon 月亮;tree 树;book 书;ball 足球;bird 鸟;banana 香蕉;flower 花; rabbit 兔子;tiger 老虎;elephant 大象;monkey 猴子;carrot 胡萝卜;orange 橙子;car 汽车;bus 公共汽车;bike 自行车;house 房子;star 星星;cake 蛋糕;
pear 梨;peach 桃子;grape 葡萄;watermelon 西瓜;strawberry 草莓;cherry 樱桃;lemon 柠檬;pineapple 菠萝;kiwi 猕猴桃;mango 芒果;coconut 椰子;blueberry 蓝莓;plum 李子;apricot 杏;avocado 牛油果;papaya 木瓜;
tomato 番茄;potato 土豆;cucumber 黄瓜;onion 洋葱;garlic 大蒜;corn 玉米;pumpkin 南瓜;cabbage 卷心菜;broccoli 西兰花;pea 豌豆;eggplant 茄子;mushroom 蘑菇;lettuce 生菜;spinach 菠菜;radish 白萝卜;chili 辣椒;
bread 面包;rice 米饭;noodle 面条;dumpling 饺子;egg 鸡蛋;milk 牛奶;cheese 奶酪;butter 黄油;yogurt 酸奶;juice 果汁;water 饮用水;tea 茶;coffee 咖啡;soup 汤;sandwich 三明治;hamburger 汉堡;
pizza 比萨;biscuit 饼干;chocolate 巧克力;candy 糖果;honey 蜂蜜;jam 果酱;sausage 香肠;steak 牛排;chicken 鸡肉;shrimp 虾;crab 螃蟹;lobster 龙虾;salad 沙拉;popcorn 爆米花;ice-cream 冰淇淋;pancake 薄煎饼;
horse 马;cow 奶牛;sheep 绵羊;goat 山羊;pig 猪;duck 鸭;goose 鹅;rooster 公鸡;chick 小鸡;panda 大熊猫;bear 棕熊;lion 狮子;wolf 狼;fox 狐狸;deer 鹿;giraffe 长颈鹿;
zebra 斑马;hippo 河马;rhino 犀牛;camel 骆驼;kangaroo 袋鼠;koala 考拉;squirrel 松鼠;hedgehog 刺猬;mouse 老鼠;hamster 仓鼠;bat 蝙蝠;otter 水獭;beaver 河狸;raccoon 浣熊;sloth 树懒;leopard 豹;
frog 青蛙;turtle 海龟;snake 蛇;lizard 蜥蜴;crocodile 鳄鱼;dolphin 海豚;whale 鲸鱼;shark 鲨鱼;octopus 章鱼;jellyfish 水母;seahorse 海马;starfish 海星;seal 海豹;penguin 企鹅;walrus 海象;goldfish 金鱼;
eagle 鹰;owl 猫头鹰;parrot 鹦鹉;peacock 孔雀;swan 天鹅;flamingo 火烈鸟;woodpecker 啄木鸟;sparrow 麻雀;ostrich 鸵鸟;butterfly 蝴蝶;bee 蜜蜂;ant 蚂蚁;ladybird 瓢虫;dragonfly 蜻蜓;snail 蜗牛;spider 蜘蛛;
shirt 衬衫;coat 大衣;jacket 夹克;dress 连衣裙;skirt 半身裙;shorts 短裤;trousers 长裤;sweater 毛衣;hat 帽子;cap 棒球帽;scarf 围巾;glove 手套;sock 袜子;shoe 鞋;boot 靴子;slipper 拖鞋;
umbrella 雨伞;backpack 双肩书包;handbag 手提包;suitcase 行李箱;belt 皮带;tie 领带;watch 手表;ring 戒指;necklace 项链;bracelet 手链;glasses 眼镜;sunglasses 太阳镜;mask 口罩;helmet 头盔;crown 王冠;raincoat 雨衣;
pencil 铅笔;pen 钢笔;eraser 橡皮;ruler 直尺;scissors 剪刀;glue 胶水;notebook 笔记本;crayon 蜡笔;paintbrush 画笔;palette 调色盘;chalk 粉笔;blackboard 黑板;desk 课桌;chair 椅子;map 地图;globe 地球仪;
clock 时钟;calendar 日历;stamp 邮票;envelope 信封;letter 信件;newspaper 报纸;magazine 杂志;dictionary 字典;calculator 计算器;computer 台式电脑;laptop 笔记本电脑;keyboard 键盘;printer 打印机;camera 照相机;phone 电话;radio 收音机;
bed 床;pillow 枕头;blanket 毯子;sofa 沙发;table 餐桌;cupboard 橱柜;shelf 书架;mirror 镜子;lamp 台灯;fan 电风扇;fridge 冰箱;oven 烤箱;kettle 水壶;toaster 烤面包机;washing-machine 洗衣机;vacuum-cleaner 吸尘器;
cup 杯子;mug 马克杯;bowl 碗;plate 盘子;spoon 勺子;fork 叉子;knife 餐刀;chopsticks 筷子;bottle 瓶子;teapot 茶壶;pan 平底锅;pot 炖锅;ladle 长柄汤勺;basket 篮子;tray 托盘;napkin 餐巾;
soap 肥皂;toothbrush 牙刷;toothpaste 牙膏;towel 毛巾;comb 梳子;shampoo 洗发水;bathtub 浴缸;shower 淋浴器;toilet 马桶;tap 水龙头;bucket 水桶;broom 扫帚;mop 拖把;sponge 海绵;bin 垃圾桶;tissue 纸巾;
train 火车;plane 飞机;ship 轮船;boat 小船;truck 卡车;taxi 出租车;ambulance 救护车;fire-engine 消防车;motorcycle 摩托车;helicopter 直升机;rocket 火箭;submarine 潜水艇;tractor 拖拉机;tram 有轨电车;van 厢式货车;canoe 独木舟;
bridge 桥;tower 高塔;castle 城堡;lighthouse 灯塔;tent 帐篷;windmill 风车;fountain 喷泉;bench 长椅;fence 栅栏;gate 大门;door 房门;window 窗户;stairs 楼梯;roof 屋顶;chimney 烟囱;balcony 阳台;
mountain 高山;river 河流;lake 湖泊;sea 大海;beach 沙滩;island 海岛;forest 森林;desert 沙漠;waterfall 瀑布;volcano 火山;cave 洞穴;hill 小山丘;rainbow 彩虹;cloud 云朵;rain 雨;snow 雪;
snowman 雪人;snowflake 雪花;lightning 闪电;tornado 龙卷风;leaf 树叶;grass 草;cactus 仙人掌;bamboo 竹子;palm 棕榈树;rose 玫瑰;tulip 郁金香;sunflower 向日葵;lotus 荷花;acorn 橡果;pine-cone 松果;rock 岩石;
guitar 吉他;piano 钢琴;violin 小提琴;drum 鼓;trumpet 小号;flute 长笛;saxophone 萨克斯;harp 竖琴;accordion 手风琴;xylophone 木琴;microphone 麦克风;headphones 耳机;bell 铃铛;whistle 哨子;maraca 沙锤;tambourine 铃鼓;
tennis-racket 网球拍;basketball 篮球;volleyball 排球;baseball 棒球;badminton 羽毛球;rugby 橄榄球;skateboard 滑板;scooter 滑板车;roller-skates 旱冰鞋;ski 滑雪板;surfboard 冲浪板;dumbbell 哑铃;trophy 奖杯;medal 奖牌;kite 风筝;balloon 气球;
robot 机器人;doll 洋娃娃;teddy-bear 玩具熊;puzzle 拼图;dice 骰子;chess 国际象棋;yo-yo 悠悠球;top 陀螺;marble 玻璃弹珠;blocks 积木;puppet 手偶;pinwheel 纸风车;drone 无人机;sledge 雪橇;boomerang 回旋镖;frisbee 飞盘;
hammer 锤子;saw 锯子;screwdriver 螺丝刀;wrench 扳手;pliers 钳子;nail 钉子;screw 螺丝;ladder 梯子;shovel 铲子;rake 耙子;axe 斧头;rope 绳子;chain 链条;key 钥匙;lock 挂锁;torch 手电筒
'''
words=[]
for item in WORDS.replace('\n','').split(';'):
    item=item.strip()
    if not item: continue
    english,chinese=item.split(' ',1)
    words.append(dict(english=english,chinese=chinese.strip(),picture=english))
assert len({w['english'] for w in words})==len(words)
# Keep 320 distinct concrete visual concepts, each receives a generated bitmap.
words=words[:320]
assert len(words)>=300

COMMON='''
一二三四五六七八九十百千万上下左右前后东西南北中大小多少长短高低远近内外里出入来去开关天日月星云雨雪风雷电水火土木金山石田地河海江湖泉林森树花草竹叶果瓜菜豆米麦禾谷玉贝虫鸟鱼龙牛羊马虎兔猪狗猫鼠鸡鸭鹅龟蛇象鹿熊猴狼狐蜂蚁蝶蛛虾蟹
人口手足头耳目眼鼻口牙舌心身子儿女父母爷奶哥姐弟妹叔伯姑姨家朋友人你我他她它们自己谁什么这那哪每各本几众全都别另其某有无是不没会能要得可应该想思知学问答说话言语读写听看见闻唱跳跑走坐站卧睡醒吃喝穿戴洗刷打扫画做劳动工作玩游戏爱喜乐欢笑哭怕惊急忙慢快好坏美丑新旧干湿冷暖热凉清轻重软硬香甜酸苦辣咸红黄蓝绿白黑紫灰粉橙青彩圆方正尖平直弯宽窄厚薄深浅空满明暗早晚春夏秋冬年周秒刻时分今昨现先再已未还就才只又也很更最太真实假信勇敢安全危危险保护助帮救找拿放收送还借买卖钱币元角存取数算加减乘除等比量秤斤克丈尺寸里公里公里计表钟针闹铃响静声音乐歌舞剧影视书报纸笔墨砚尺规橡皮擦图字词句篇文章诗词故事名姓写记忆念忘认识认练习复考试查错对题奖罚分成败胜输赢力气努力勤劳认真专细粗严松紧耐久坚强弱软顽皮聪慧智理懂感情温和善恶勇猛奋斗进步退落升降起终结束完毕始初末底顶边旁侧角面背腰腹腿膝脚指掌臂肩胸脑血骨肉皮毛发脸眉嘴唇齿胃肺肝肚肠病医药痛痒伤健康救护床枕被毯灯桌椅板柜架箱包袋盒盆碗杯盘筷刀叉勺锅灶炉油盐酱醋糖茶酒汤饭粥面饼包馒饺粽糕蛋奶乳粉汁肉鸡鸭鱼虾蟹虾葱姜蒜椒茄瓜萝卜薯藕芋菇菌芽蔬梨桃杏李梅枣葡萄柿桔橘橙柚柠檬荔枝榴莲莓樱蕉椰枫柳杨松柏槐榆杉榕桂莲荷菊兰牡丹玫瑰茶稻穗麦棉麻布丝绸衣衫裤裙袜鞋帽巾带纽扣针线棉绒羽绳网伞扇灯镜梳刷皂桶盆浴澡洁净脏污尘灰扫拖擦抹吸冲浇灌种栽苗根茎枝芽蕾瓣开结熟落收获获田园农牧渔村乡镇县市区省国都京城街道路桥站港湾码头船舟艇帆车轮胎轴座汽油轨铁铜银钢铝矿煤沙砂泥砖瓦墙屋房室厅厨卫窗门锁钥楼梯阶层台院庭亭廊塔寺庙宫殿馆店铺商场超集市厂校医院银邮局警站公园操场跑道泳池滑梯秋千沙坑足球篮球排球羽网拍乒乓棋棋棋棋琴鼓笛箫号铃管弦弹奏吹敲击握抓捏推拉抬举扔投接踢拍碰撞转旋拐绕圈穿跨越爬攀登踩踏蹲跪躺翻滚滑溜漂游潜浮沉飞翔跃窜蹦扑追赶奔逃停留等待准备开始继续暂停重复保存记录成绩历史时间目标方法方向位置距离速度力量角度大小颜色形状图案比较判断观察计划选择确定取消返回退出设置帮助提示规则伙伴轮流合作分享礼让尊重公平诚实守时守信
'''

def build():
    old=(OUT/'learning_data.gd').read_text('utf-8')
    initial=json.loads(re.search(r'const POEMS = (\[.*\])',old).group(1))
    corpus=json.loads((SRC/'qianjiashi-simplified.json').read_text('utf-8'))
    poems=[]; seen={}
    for p in initial:
        poems.append({k:p[k] for k in ['title','author','first','next']}); seen[p['first']]=p['next']
    for group in corpus['content']:
        for p in group['content']:
            lines=[]
            for value in p['paragraphs']:
                lines.extend(value['paragraphs'] if isinstance(value,dict) else [value])
            for line in lines:
                pair=[x.strip() for x in re.split('[，。？！；：]',line) if x.strip()]
                if len(pair)!=2 or len(pair[0]) not in [5,7] or len(pair[0])!=len(pair[1]): continue
                if not all('\u4e00'<=c<='\u9fff' for c in ''.join(pair)): continue
                if pair[0] in seen: continue
                seen[pair[0]]=pair[1]
                poems.append(dict(title=p['chapter'],author=p['author'],first=pair[0],next=pair[1]))
    readings={}
    for line in (SRC/'pinyin.txt').read_text('utf-8').splitlines():
        m=re.match(r'U\+([0-9A-F]+): ([^ #]+)',line)
        if m: readings[chr(int(m[1],16))]=m[2].split(',')
    syllables=[]; used=set()
    for ch in COMMON:
        if ch in used: continue
        used.add(ch)
        values=readings.get(ch,[])
        # Bare characters with multiple readings are ambiguous; omit them.
        if len(values)!=1: continue
        full=values[0]
        plain=''.join(c for c in unicodedata.normalize('NFD',full) if c not in '\u0304\u0301\u030c\u0300')
        plain=unicodedata.normalize('NFC',plain)
        m=re.match(r'^(zh|ch|sh|[bpmfdtnlgkhjqxrzcs])(.+)$',plain)
        if not m: continue
        initial,final=m.groups()
        if initial in 'jqx' and final.startswith('u'): final='ü'+final[1:]
        if final not in ['a','o','e','i','u','ü','ai','ei','ao','ou','an','en','ang','eng','ong','ia','ie','iao','iu','ian','in','iang','ing','iong','ua','uo','uai','ui','uan','un','uang','ueng','üe','üan','ün']: continue
        syllables.append(dict(word=ch,initial=initial,final=final,full=full))
    syllables=syllables[:450]
    assert len(poems)>=300 and len(syllables)>=300,(len(poems),len(syllables))
    for name,data in [('POEMS',poems),('SYLLABLES',syllables),('WORDS',words)]:
        text=json.dumps(data,ensure_ascii=False,separators=(',',':'))
        if name=='POEMS': out='extends RefCounted\n# Offline curated banks; see assets/licenses/learning-sources.txt.\n'
        out+=f'const {name} = {text}\n'
    (OUT/'learning_data.gd').write_text(out,encoding='utf-8')
    (SRC/'english-visual-concepts.json').write_text(json.dumps(words,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(dict(poetry=len(poems),pinyin=len(syllables),english=len(words))))
if __name__=='__main__': build()
