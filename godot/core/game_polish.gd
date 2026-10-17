extends RefCounted
# 小尺寸SVG只在第一次使用时栅格化，之后复用Texture2D；无Shader、无离屏特效。
static var textures: Dictionary = {}
static func _svg(key: String, body: String, width: int = 128, height: int = 128) -> Texture2D:
    if textures.has(key): return textures[key]
    var image := Image.new()
    var error: Error = image.load_svg_from_string('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">'%[width,height,width,height]+body+'</svg>')
    if error != OK: push_error("美术纹理生成失败："+key); return null
    image.generate_mipmaps()
    var texture := ImageTexture.create_from_image(image)
    textures[key] = texture
    return texture
static func _hex(tint: Color) -> String: return "#"+tint.to_html(false)
static func tile(v, rect: Rect2, tint: Color, depth: float = 3) -> void:
    # 一张中性材质用顶点色染色，所有彩块可共用同一纹理批次。
    var key: String = "tile-neutral"
    if depth > 0: v._box(Rect2(rect.position+Vector2(0,depth),rect.size),tint.darkened(0.35),12)
    if not textures.has(key):
        _svg(key,'<defs><linearGradient id="g" x2="0" y2="1"><stop stop-color="#ffffff"/><stop offset="1" stop-color="#bfc4ca"/></linearGradient></defs><rect x="1" y="1" width="126" height="126" rx="15" fill="url(#g)" stroke="#a6aeb7" stroke-width="2"/><path d="M16 5h96q11 0 11 12v24Q67 16 5 47V17Q5 5 16 5" fill="#fff" opacity=".12"/>')
    v.draw_texture_rect(textures[key],rect,false,tint.lightened(0.09))
static func orb(v, at: Vector2, radius: float, tint: Color, shadow: bool = true) -> void:
    var key: String = "orb"+tint.to_html(false)
    if textures.has(key):
        if shadow: v.draw_circle(at+Vector2(0,radius*0.13),radius,Color("16283a25"))
        v.draw_texture_rect(textures[key],Rect2(at-Vector2.ONE*radius,Vector2.ONE*radius*2),false); return
    var body: String = '<defs><radialGradient id="g" cx=".30" cy=".24" r=".78"><stop stop-color="%s"/><stop offset=".47" stop-color="%s"/><stop offset="1" stop-color="%s"/></radialGradient></defs><circle cx="48" cy="48" r="45" fill="url(#g)" stroke="%s" stroke-width="1.5"/><ellipse cx="34" cy="26" rx="16" ry="9" fill="#fff" opacity=".28"/>'%[_hex(tint.lightened(0.50)),_hex(tint),_hex(tint.darkened(0.36)),_hex(tint.darkened(0.25))]
    if shadow:
        v.draw_set_transform(at+Vector2(0,radius*0.13),0,Vector2(1,0.90))
        v.draw_circle(Vector2.ZERO,radius,Color("16283a25"))
        v.draw_set_transform(Vector2.ZERO)
    v.draw_texture_rect(_svg(key,body,96,96),Rect2(at-Vector2.ONE*radius,Vector2.ONE*radius*2),false)
static func surface(v, rect: Rect2, kind: String) -> void:
    if textures.has("surface"+kind): v.draw_texture_rect(textures["surface"+kind],rect,false); return
    var body: String
    if kind == "wood":
        body = '<defs><linearGradient id="g" x2=".8" y2="1"><stop stop-color="#f7e4b8"/><stop offset=".5" stop-color="#e7c88f"/><stop offset="1" stop-color="#dfbb7b"/></linearGradient></defs><rect width="256" height="256" rx="5" fill="url(#g)"/>'
        for i in range(14):
            var y: int = i*19
            body += '<path d="M0 %dC54 %d 88 %d 136 %dS222 %d 256 %d" fill="none" stroke="#886031" stroke-width=".7" opacity=".10"/>'%[y,y-7,y+5,y,y-6,y+3]
    elif kind == "felt":
        body = '<defs><linearGradient id="g" x2=".9" y2="1"><stop stop-color="#3b8169"/><stop offset="1" stop-color="#245343"/></linearGradient><pattern id="p" width="6" height="6" patternUnits="userSpaceOnUse"><path d="M0 6 6 0" stroke="#d0eadb" stroke-width=".6" opacity=".08"/></pattern></defs><rect width="256" height="256" fill="url(#g)"/><rect width="256" height="256" fill="url(#p)"/>'
    elif kind == "ice":
        body = '<defs><linearGradient id="g" x2=".8" y2="1"><stop stop-color="#f2fbfc"/><stop offset=".48" stop-color="#d7edf2"/><stop offset="1" stop-color="#ecf6f8"/></linearGradient></defs><rect width="256" height="256" fill="url(#g)"/>'
        for i in range(18): body += '<path d="M%d %d l46 -64" stroke="#86b4c6" stroke-width=".5" opacity=".12"/>'%[(i*47)%256,(i*61)%290]
    elif kind == "garden":
        body = '<defs><linearGradient id="g" x2="0" y2="1"><stop stop-color="#d7e6b7"/><stop offset="1" stop-color="#b1c488"/></linearGradient></defs><rect width="256" height="256" rx="8" fill="url(#g)"/>'
        for i in range(28):
            var x: int = (i*71)%256
            var y: int = (i*43)%256
            body += '<path d="M%d %d l-3 -5 m3 5 3 -6" stroke="#668849" stroke-width=".8" opacity=".24"/>'%[x,y]
    else:
        body = '<rect width="256" height="256" fill="#faf5e8"/><path d="M0 48h256M0 112h256M0 176h256M0 240h256" stroke="#d8c39b" opacity=".30"/><path d="M76 0v48m110 0v64M68 112v64m125 0v64" stroke="#d8c39b" opacity=".20"/>'
    v.draw_texture_rect(_svg("surface"+kind,body,256,256),rect,false)
static func checker(v, rect: Rect2, cols: int, rows: int) -> void:
    var key: String = "checker%d_%d"%[cols,rows]
    var body: String = '<rect width="%d" height="%d" fill="#e5edd6"/>'%[cols*24,rows*24]
    if not textures.has(key):
        for y in range(rows):
            for x in range(cols):
                if (x+y)%2 == 0: body += '<rect x="%d" y="%d" width="24" height="24" fill="#d9e5c8"/>'%[x*24,y*24]
        for x in range(cols+1): body += '<path d="M%d 0v%d" stroke="#9db18c" stroke-width=".8"/>'%[x*24,rows*24]
        for y in range(rows+1): body += '<path d="M0 %dh%d" stroke="#9db18c" stroke-width=".8"/>'%[y*24,cols*24]
    v.draw_texture_rect(_svg(key,body,cols*24,rows*24),rect,false)
static func fruit(v, at: Vector2, radius: float, index: int, bomb: bool = false) -> void:
    var colors: Array = ["db594c","f0a43c","d75b4f","e8c852","9864a9","92ad4d","d65456","c54948"]
    var key: String = "fruit%d"%index if not bomb else "bomb"
    if textures.has(key): v.draw_texture_rect(textures[key],Rect2(at-Vector2.ONE*radius*1.12,Vector2.ONE*radius*2.24),false); return
    var tint := Color(colors[posmod(index,8)])
    var body: String = '<defs><radialGradient id="g" cx=".3" cy=".22" r=".8"><stop stop-color="%s"/><stop offset=".45" stop-color="%s"/><stop offset="1" stop-color="%s"/></radialGradient></defs>'%[_hex(tint.lightened(0.3)),_hex(tint),_hex(tint.darkened(0.3))]
    if bomb:
        body = '<defs><radialGradient id="b" cx=".3" cy=".25" r=".75"><stop stop-color="#758091"/><stop offset=".5" stop-color="#34394b"/><stop offset="1" stop-color="#171b27"/></radialGradient></defs><path d="M64 27q-8-19 13-18" stroke="#ad8550" stroke-width="5" fill="none"/><circle cx="64" cy="73" r="46" fill="url(#b)"/><rect x="53" y="20" width="22" height="15" rx="3" fill="#3d4252"/><path d="m79 7 6-6m-4 12 10 1m-14-3 0-10" stroke="#e9af4d" stroke-width="3"/><path d="m48 57 32 31m0-31L48 88" stroke="#ede8db" stroke-width="5" stroke-linecap="round"/>'
    elif index == 0: # 西瓜剖面，种子与瓜皮一眼可辨。
        body += '<circle cx="64" cy="68" r="52" fill="#548548"/><circle cx="64" cy="68" r="45" fill="#f4e9bf"/><circle cx="64" cy="68" r="40" fill="url(#g)"/>'
        for i in range(8):
            var angle: float = TAU*i/8
            body += '<ellipse cx="%.1f" cy="%.1f" rx="2.5" ry="4" fill="#513b35" transform="rotate(%d %.1f %.1f)"/>'%[64+cos(angle)*25,68+sin(angle)*25,i*45,64+cos(angle)*25,68+sin(angle)*25]
    elif index == 4: # 葡萄
        for point in [Vector2(45,47),Vector2(76,43),Vector2(94,68),Vector2(60,72),Vector2(39,80),Vector2(73,99)]: body += '<circle cx="%d" cy="%d" r="21" fill="url(#g)" stroke="#71517c" stroke-width="1.5"/>'%[point.x,point.y]
    elif index == 3: # 香蕉
        body += '<path d="M18 39q31 62 91-8-3 80-65 78Q15 92 18 39" fill="url(#g)" stroke="#b19235" stroke-width="2"/><path d="M28 54q26 57 69-7" fill="none" stroke="#fff0a0" stroke-width="5"/><path d="m17 37 6-4m82-3 7 5" stroke="#826336" stroke-width="8"/>'
    elif index == 5: # 猕猴桃切面
        body += '<circle cx="64" cy="70" r="50" fill="#97734d"/><circle cx="64" cy="70" r="44" fill="url(#g)"/><ellipse cx="64" cy="70" rx="15" ry="21" fill="#e4dda2"/>'
        for i in range(14):
            var angle: float = TAU*i/14
            body += '<ellipse cx="%.1f" cy="%.1f" rx="1.8" ry="3" fill="#4a4c32"/>'%[64+cos(angle)*27,70+sin(angle)*28]
    elif index == 6: # 草莓
        body += '<path d="M64 116C49 114 9 71 19 44c8-24 29-23 45-15 16-8 37-9 45 15 10 27-30 70-45 72" fill="url(#g)"/><path d="m64 37-24-16 18 2 6-15 8 15 21-1-27 15" fill="#5f8d48"/>'
        for i in range(13): body += '<ellipse cx="%d" cy="%d" rx="1.8" ry="3" fill="#f6dc8d" transform="rotate(15 64 64)"/>'%[32+(i*17)%62,46+(i*13)%51]
    elif index == 7: # 樱桃
        body += '<path d="M39 72Q37 38 67 12 99 37 94 70M66 13Q50 5 47 9" fill="none" stroke="#637a45" stroke-width="4"/><circle cx="38" cy="88" r="29" fill="url(#g)"/><circle cx="91" cy="88" r="28" fill="url(#g)"/><ellipse cx="28" cy="77" rx="6" ry="10" fill="#fff" opacity=".2"/>'
    else:
        body += '<path d="M64 30c-19-17-55 1-50 40 4 36 27 53 50 43 23 10 46-7 50-43 5-39-31-57-50-40" fill="url(#g)"/>' if index == 2 else '<circle cx="64" cy="73" r="48" fill="url(#g)"/>'
        body += '<ellipse cx="43" cy="49" rx="11" ry="17" fill="#fff" opacity=".18"/>'
        if index == 1:
            for i in range(14): body += '<circle cx="%d" cy="%d" r="1.2" fill="#ffe0a4" opacity=".5"/>'%[30+(i*17)%68,43+(i*13)%62]
    if not bomb and index not in [3,5,6,7]:
        body += '<path d="M64 31 67 12" stroke="#775738" stroke-width="5" stroke-linecap="round"/><path d="M67 19q11-25 31-10Q88 29 67 19" fill="#579252"/>'
    v.draw_texture_rect(_svg(key,body),Rect2(at-Vector2.ONE*radius*1.12,Vector2.ONE*radius*2.24),false)
static func mole(v, at: Vector2, radius: float) -> void:
    if textures.has("mole"): v.draw_texture_rect(textures.mole,Rect2(at-Vector2.ONE*radius,Vector2.ONE*radius*2),false); return
    var body: String = '<defs><radialGradient id="fur" cx=".3" cy=".2" r=".9"><stop stop-color="#c9ad8d"/><stop offset=".55" stop-color="#9c7c63"/><stop offset="1" stop-color="#705744"/></radialGradient><radialGradient id="m"><stop stop-color="#f0d9bd"/><stop offset="1" stop-color="#cfb18d"/></radialGradient></defs><ellipse cx="64" cy="107" rx="43" ry="21" fill="#8c7056"/><circle cx="28" cy="36" r="17" fill="#97735d"/><circle cx="100" cy="36" r="17" fill="#97735d"/><circle cx="28" cy="36" r="10" fill="#c79c88"/><circle cx="100" cy="36" r="10" fill="#c79c88"/><ellipse cx="64" cy="70" rx="47" ry="48" fill="url(#fur)"/><path d="m52 27 8-8 5 8 8-5" stroke="#ba9b7b" fill="none" stroke-width="4"/><ellipse cx="64" cy="90" rx="27" ry="21" fill="url(#m)"/><ellipse cx="43" cy="65" rx="7" ry="9" fill="#302924"/><ellipse cx="85" cy="65" rx="7" ry="9" fill="#302924"/><circle cx="41" cy="62" r="2.7" fill="#fff"/><circle cx="83" cy="62" r="2.7" fill="#fff"/><ellipse cx="64" cy="82" rx="11" ry="8" fill="#775246"/><path d="M64 88v7m-9 0q9 9 18 0" stroke="#7a5a47" stroke-width="2" fill="none"/><path d="M47 88 16 82m31 14-30 4m64-12 31-6M81 96l30 4" stroke="#725a48" stroke-width="2"/><ellipse cx="31" cy="108" rx="13" ry="8" fill="#d1b58d"/><ellipse cx="97" cy="108" rx="13" ry="8" fill="#d1b58d"/>'
    v.draw_texture_rect(_svg("mole",body),Rect2(at-Vector2.ONE*radius,Vector2.ONE*radius*2),false)
