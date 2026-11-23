def sprig(size, pose='idle'):
    aL, aR = {'guard':(8,138),'cheer':(150,150),'idle':(0,0),'wave':(0,140)}[pose]
    joy = pose=='cheer'
    eyes = ('<g stroke="#1E1B3C" stroke-width="3" stroke-linecap="round" fill="none"><path d="M39 72 Q45 63 51 72"/><path d="M69 72 Q75 63 81 72"/></g>' if joy else
            '<circle cx="45" cy="70" r="5.2" fill="#1E1B3C"/><circle cx="75" cy="70" r="5.2" fill="#1E1B3C"/><circle cx="46.8" cy="68" r="1.7" fill="#fff"/><circle cx="76.8" cy="68" r="1.7" fill="#fff"/>')
    mouth = {'guard':'<path d="M55 83 H65" stroke="#1E1B3C" stroke-width="2.6" stroke-linecap="round" fill="none"/>',
             'cheer':'<path d="M53 79 Q60 93 67 79 Z" fill="#1E1B3C"/>'}.get(pose,'<path d="M54 80 Q60 87 66 80" stroke="#1E1B3C" stroke-width="2.6" stroke-linecap="round" fill="none"/>')
    return f'''<svg viewBox="0 0 120 120" width="{size}" height="{size}" style="display:block;overflow:visible"><defs><clipPath id="c{size}{pose}"><rect x="-60" y="-80" width="240" height="200"/></clipPath></defs><g clip-path="url(#c{size}{pose})">
<g transform="rotate({aL} 24 90)"><path d="M24 90 V110" stroke="#6B5BE0" stroke-width="12" stroke-linecap="round" fill="none"/></g>
<g transform="rotate({-aR} 96 90)"><path d="M96 90 V110" stroke="#6B5BE0" stroke-width="12" stroke-linecap="round" fill="none"/></g>
<g><path d="M60 38 C60 30 61 24 60 17" stroke="#218A52" stroke-width="3" stroke-linecap="round" fill="none"/>
<path d="M60 24 C50 24 44 18 44 10 C54 10 60 16 60 24 Z" fill="#2FA866"/><path d="M60 20 C70 20 77 14 77 6 C66 6 60 12 60 20 Z" fill="#5CCB86"/>
<circle cx="60" cy="9" r="4" fill="#FF8FB0"/><circle cx="55" cy="13" r="4" fill="#FF8FB0"/><circle cx="65" cy="13" r="4" fill="#FF8FB0"/><circle cx="60" cy="13" r="2.6" fill="#FFD25E"/></g>
<path d="M16 160 V84 C16 52 34 34 60 34 C86 34 104 52 104 84 V160 Z" fill="#8B7CF6"/>
<path d="M16 100 Q60 110 104 100 V160 H16 Z" fill="#FFB84D"/><path d="M16 100 Q60 110 104 100" stroke="#E0952A" stroke-width="3" fill="none"/><circle cx="60" cy="114" r="1.6" fill="#E0952A"/><circle cx="60" cy="120" r="1.6" fill="#E0952A"/>
<ellipse cx="33" cy="81" rx="6.5" ry="4.2" fill="#FF9EB5" opacity="0.55"/><ellipse cx="87" cy="81" rx="6.5" ry="4.2" fill="#FF9EB5" opacity="0.55"/>
{eyes}{mouth}</g></svg>'''

TICK = '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>'
HEAD = '''<!doctype html><html><head><meta charset="utf-8"><style>
body{margin:0;background:#000}*{box-sizing:border-box}
.ph{width:390px;height:844px;overflow:hidden;display:flex;flex-direction:column;position:relative;font-family:'Bricolage Grotesque',system-ui,sans-serif}
button{font:inherit}
</style></head><body>'''
def statusbar(ink):
    return f'''<div style="flex:none;height:28px;display:flex;justify-content:space-between;align-items:center;padding:0 22px;font-size:13px;font-weight:700;color:{ink};position:relative;z-index:3"><span>7:02</span><span style="display:flex;gap:6px;align-items:center"><svg width="15" height="11" viewBox="0 0 15 11"><path d="M1 10h2V7H1zM5 10h2V5H5zM9 10h2V3H9zM13 10h2V1h-2z" fill="{ink}"/></svg><svg width="20" height="11" viewBox="0 0 20 11"><rect x=".5" y=".5" width="17" height="10" rx="2.5" fill="none" stroke="{ink}"/><rect x="2" y="2" width="11" height="7" rx="1" fill="{ink}"/><rect x="18.3" y="3.5" width="1.5" height="4" rx=".7" fill="{ink}"/></svg></span></div>'''

def weekstrip(marks, dark, size=30):
    labels=['Su','Mo','Tu','We','Th','Fr','Sa']
    soft = '#D8D3F5' if dark else '#5C5878'
    out=[]
    for l,m in zip(labels,marks):
        inner=''
        if m=='k': st=f'background:#FFB81F;border:2px solid #FFB81F'; inner=TICK.format(c='#1E1B3C')
        elif m=='x': st=('background:#2E2A5E;border:2px solid #4A4590' if dark else 'background:#F1EBE2;border:2px solid #E0D6C8'); inner=f'<span style="width:10px;height:2.5px;border-radius:2px;background:{"#7D77B8" if dark else "#B5AEC4"}"></span>'
        elif m=='t': st=f'background:transparent;border:2px dashed {"#FFC23D" if dark else "#5B4BD6"}'
        else: st=f'background:transparent;border:2px solid {"#4A4590" if dark else "#E0D6C8"}'
        bold = 'color:#FFC23D;font-weight:800' if (m=='t' and dark) else ('color:#5B4BD6;font-weight:800' if m=='t' else '')
        out.append(f'<div style="display:flex;flex-direction:column;align-items:center;gap:5px"><span style="width:{size}px;height:{size}px;border-radius:50%;display:flex;align-items:center;justify-content:center;{st}">{inner}</span><span style="font-size:12px;font-weight:600;color:{soft};{bold}">{l}</span></div>')
    return '<div style="display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:6px">'+''.join(out)+'</div>'

# 1 SETUP
chips=['Exercise','Read','Practice','Study','Tidy room']
chiphtml=''.join(f'<span style="height:36px;padding:0 14px;border-radius:18px;display:inline-flex;align-items:center;font-size:14px;font-weight:{700 if c=="Exercise" else 600};background:{"#5B4BD6" if c=="Exercise" else "#ECE9FF"};color:{"#fff" if c=="Exercise" else "#3A2FA0"}">{c}</span>' for c in chips)
chiphtml+='<span style="height:36px;padding:0 14px;border-radius:18px;display:inline-flex;align-items:center;gap:4px;font-size:14px;font-weight:600;border:1.5px dashed #B9B0F7;color:#3A2FA0">+ Custom</span>'
days=''.join(f'<span style="height:48px;border-radius:24px;display:flex;align-items:center;justify-content:center;font-size:18px;font-weight:800;font-variant-numeric:tabular-nums;{"background:#5B4BD6;color:#fff" if d==4 else "background:#fff;border:1.5px solid #E6DCCE;color:#1E1B3C"}">{d}</span>' for d in range(1,8))
setup = HEAD+f'''<div class="ph" style="background:#FFF9F1;color:#1E1B3C">{statusbar('#1E1B3C')}
<div style="flex:none;padding:4px 12px 4px"><span style="display:flex;align-items:center;justify-content:center;width:44px;height:44px"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#1E1B3C" stroke-width="2.4" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg></span></div>
<div style="flex:1;display:flex;flex-direction:column;gap:26px;padding:4px 24px 12px;position:relative">
<div style="position:absolute;right:14px;top:-34px">{sprig(84,'wave')}</div>
<div><h1 style="margin:0;font-size:36px;line-height:1;font-weight:800;letter-spacing:-0.02em">Weekly goal</h1>
<p style="margin:8px 0 0;font-size:15px;line-height:1.35;font-weight:500;color:#5C5878;max-width:250px">For things you don't need to do every day.</p></div>
<div style="display:flex;flex-direction:column;gap:12px">
<span style="font-size:15px;font-weight:700">What will you do?</span>
<div style="height:60px;padding:0 18px;border-radius:20px;border:2px solid #5B4BD6;background:#fff;font-size:19px;font-weight:700;display:flex;align-items:center">Exercise<span style="width:2px;height:24px;background:#5B4BD6;margin-left:2px"></span></div>
<div style="display:flex;flex-wrap:wrap;gap:8px">{chiphtml}</div></div>
<div style="display:flex;flex-direction:column;gap:12px">
<div style="display:flex;justify-content:space-between;align-items:baseline"><span style="font-size:15px;font-weight:700">How many days a week?</span><span style="font-size:14px;font-weight:600;color:#5C5878">4 of 7</span></div>
<div style="display:grid;grid-template-columns:repeat(7,1fr);gap:6px">{days}</div></div>
<div style="padding:16px 18px;border-radius:20px;background:#ECE9FF;display:flex;flex-direction:column;gap:6px">
<span style="font-size:17px;font-weight:800">Exercise, 4 days a week</span>
<span style="font-size:13.5px;line-height:1.4;font-weight:500;color:#3A3658">Week runs Sunday to Saturday. If you fall behind, KEPT locks your phone on the day you can't skip anymore.</span></div>
</div>
<div style="flex:none;padding:12px 20px 28px"><div style="display:flex;align-items:center;justify-content:center;height:56px;border-radius:28px;background:#1E1B3C;color:#FFF9F1;font-size:17px;font-weight:700">Save goal</div></div>
</div></body></html>'''

# 2 NOTIFICATION
icon='<span style="width:22px;height:22px;border-radius:50%;background:#534AB7;display:flex;align-items:center;justify-content:center;overflow:hidden"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round"><path d="M12 21v-9M12 12c-4 0-6-2-6-6 4 0 6 2 6 6zM12 15c3.5 0 5.5-2 5.5-5.5-3.5 0-5.5 2-5.5 5.5z"/></svg></span>'
notif = HEAD+f'''<div class="ph" style="background:linear-gradient(180deg,#2B2760 0%,#3E3478 38%,#6E5590 62%,#B57F98 82%,#E9A99A 100%);color:#FFF4E4">{statusbar('#FFF4E4')}
<div style="position:absolute;inset:0;background:rgba(14,12,36,0.45)"></div>
<div style="position:relative;z-index:2;display:flex;flex-direction:column;align-items:center;padding-top:46px">
<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#FFF4E4" stroke-width="2.2" stroke-linecap="round"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>
<div style="font-size:92px;line-height:1;font-weight:500;letter-spacing:-0.03em;margin-top:14px;font-variant-numeric:tabular-nums">7:02</div>
<div style="font-size:16px;font-weight:600;margin-top:8px;color:#D8D3F5">Wednesday, Nov 25</div></div>
<div style="position:relative;z-index:2;margin:40px 14px 0;border-radius:26px;background:#FFF9F1;color:#1E1B3C;padding:16px 18px 14px;box-shadow:0 12px 36px rgba(0,0,0,0.35)">
<div style="display:flex;align-items:center;gap:8px;font-size:12.5px;font-weight:600;color:#5C5878">{icon}<span style="color:#1E1B3C;font-weight:700">KEPT</span><span>· now</span><span style="margin-left:auto"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#5C5878" stroke-width="2.4" stroke-linecap="round"><path d="M6 15l6-6 6 6"/></svg></span></div>
<div style="display:flex;gap:12px;margin-top:10px;align-items:flex-start">
<div style="flex:1"><div style="font-size:17px;font-weight:800;line-height:1.2">No exercise yet this week</div>
<div style="font-size:14.5px;line-height:1.4;font-weight:500;color:#3A3658;margin-top:4px">Only 4 days left and you need 4. Your apps are locked until today's session is done.</div></div>
<div style="flex:none;width:52px;height:52px;border-radius:14px;background:#2B2760;display:flex;align-items:flex-end;justify-content:center;overflow:hidden">{sprig(48,'guard')}</div></div>
<div style="display:flex;gap:8px;margin-top:14px">
<span style="height:36px;padding:0 16px;border-radius:18px;background:#1E1B3C;color:#FFF9F1;font-size:14px;font-weight:700;display:flex;align-items:center">Mark done</span>
<span style="height:36px;padding:0 16px;border-radius:18px;background:#ECE9FF;color:#3A2FA0;font-size:14px;font-weight:700;display:flex;align-items:center">Open KEPT</span></div></div>
<div style="position:relative;z-index:2;margin:10px 14px 0;border-radius:22px;background:rgba(255,249,241,0.16);padding:12px 18px;display:flex;align-items:center;gap:10px;font-size:13.5px;font-weight:600;color:#E9E5FF"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>Messages · Phone · Maps still work</div>
<div style="position:absolute;z-index:2;left:0;right:0;bottom:30px;display:flex;justify-content:space-between;padding:0 40px;align-items:center">
<span style="width:48px;height:48px;border-radius:50%;background:rgba(255,255,255,0.16);display:flex;align-items:center;justify-content:center"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#FFF4E4" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z"/></svg></span>
<span style="width:120px;height:5px;border-radius:3px;background:rgba(255,255,255,0.7)"></span>
<span style="width:48px;height:48px;border-radius:50%;background:rgba(255,255,255,0.16);display:flex;align-items:center;justify-content:center"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#FFF4E4" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8h3l2-3h6l2 3h3v11H4z"/><circle cx="12" cy="13" r="3.5"/></svg></span></div>
</div></body></html>'''

# 3 LOCK
lock = HEAD+f'''<div class="ph" style="background:#1B1842;color:#FFF4E4">
<div style="position:relative;flex:none;height:300px;overflow:hidden;background:#2B2760">{statusbar('#FFF4E4')}
<span style="position:absolute;left:24px;top:40px;display:inline-flex;align-items:center;gap:6px;height:30px;padding:0 12px;border-radius:15px;background:rgba(255,255,255,0.12);font-size:13px;font-weight:700;color:#D8D3F5"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>Weekly goal</span>
<div style="position:absolute;left:24px;top:86px;width:200px">
<h1 style="margin:0;font-size:36px;line-height:1.02;font-weight:800;letter-spacing:-0.02em">4 days left, 4 to go.</h1>
<p style="margin:10px 0 0;font-size:15px;line-height:1.35;font-weight:500;color:#D8D3F5">No exercise yet this week. Do today's session to unlock.</p></div>
<div style="position:absolute;left:196px;top:104px">{sprig(180,'guard')}</div></div>
<div style="position:relative;flex:none;height:52px;margin:-48px -60px -4px;background:#1B1842;border-radius:50% 50% 0 0/48px 48px 0 0"></div>
<div style="flex:none;display:flex;justify-content:space-between;align-items:baseline;padding:2px 24px 12px"><h2 style="margin:0;font-size:18px;font-weight:700">This week</h2><p style="margin:0;font-size:14px;font-weight:600;color:#D8D3F5">Exercise · 0 of 4</p></div>
<div style="flex:none;padding:0 24px">{weekstrip(['x','x','x','t','u','u','u'],True,34)}</div>
<div style="flex:none;display:flex;gap:14px;padding:12px 24px 0;font-size:12px;font-weight:600;color:#A9A3DC">
<span style="display:flex;align-items:center;gap:5px"><span style="width:10px;height:10px;border-radius:5px;background:#FFB81F"></span>Done</span>
<span style="display:flex;align-items:center;gap:5px"><span style="width:10px;height:10px;border-radius:5px;background:#2E2A5E;border:1.5px solid #4A4590"></span>Skipped</span>
<span style="display:flex;align-items:center;gap:5px"><span style="width:10px;height:10px;border-radius:5px;border:1.5px dashed #FFC23D"></span>Today</span>
<span style="display:flex;align-items:center;gap:5px"><span style="width:10px;height:10px;border-radius:5px;border:1.5px solid #4A4590"></span>Coming up</span></div>
<div style="flex:1;display:flex;flex-direction:column;justify-content:flex-start;padding:28px 20px 14px">
<div style="display:flex;align-items:center;gap:12px;height:56px;padding:0 14px;border-radius:18px;border:2px solid #4A4590">
<span style="width:28px;height:28px;border-radius:14px;border:2px solid #A9A3DC"></span><span style="flex:1;font-size:16px;font-weight:700">Exercise</span><span style="font-size:13px;font-weight:500;color:#D8D3F5">Today</span></div>
<p style="margin:12px 4px 0;font-size:13px;line-height:1.4;font-weight:500;color:#A9A3DC">Skip today and tomorrow locks too. Every day from here counts.</p></div>
<div style="flex:none;padding:12px 20px 28px;border-top:1.5px solid #2E2A5E;display:flex;flex-direction:column;gap:12px">
<div style="display:flex;align-items:center;justify-content:center;gap:8px;height:56px;border-radius:28px;background:#FFC23D;color:#1E1B3C;font-size:17px;font-weight:800">{TICK.format(c='#1E1B3C').replace('15','18')}Mark today's exercise done</div>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
<span style="display:flex;align-items:center;justify-content:center;gap:8px;height:52px;border-radius:26px;border:2px solid #FFF4E4;font-size:16px;font-weight:700"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z"/></svg>Phone</span>
<span style="display:flex;align-items:center;justify-content:center;height:52px;font-size:16px;font-weight:600;color:#D8D3F5;text-decoration:underline">Break the lock</span></div></div>
</div></body></html>'''

# 4 HOME
habits=[('Read','10 pages',True),('Practice guitar','20 min',True),('Study','45 min',False),('Tidy room','Photo',False)]
hh=''
for t,d,done in habits:
    hh+=f'<div style="display:flex;align-items:center;gap:12px;height:52px;padding:0 14px;border-radius:18px;border:2px solid {"#B9B0F7" if done else "#EADFD2"};background:{"#ECE9FF" if done else "#fff"}"><span style="width:28px;height:28px;border-radius:14px;display:flex;align-items:center;justify-content:center;border:2px solid {"#5B4BD6" if done else "#8D88A8"};background:{"#5B4BD6" if done else "transparent"}">{TICK.format(c="#fff") if done else ""}</span><span style="flex:1;font-size:16px;font-weight:700;text-decoration:{"line-through" if done else "none"}">{t}</span><span style="font-size:13px;font-weight:500;color:#5C5878">{"Done" if done else d}</span></div>'
pips=''.join(f'<span style="flex:1;height:10px;border-radius:5px;background:{"#FFB81F" if i<1 else "#EFE4D6"}"></span>' for i in range(4))
home = HEAD+f'''<div class="ph" style="background:#FFF9F1;color:#1E1B3C">
<div style="position:relative;flex:none;height:250px;overflow:hidden;background:#E9A99A">{statusbar('#1E1B3C')}
<span style="position:absolute;left:24px;top:40px;display:inline-flex;align-items:center;gap:6px;height:32px;padding:0 12px;border-radius:16px;background:rgba(255,255,255,0.28);font-size:14px;font-weight:700"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3 0-6 1-9z"/></svg>12 day streak</span>
<div style="position:absolute;left:24px;top:88px;width:180px"><h1 style="margin:0;font-size:38px;line-height:1;font-weight:800;letter-spacing:-0.02em">Two to go.</h1><p style="margin:8px 0 0;font-size:15px;line-height:1.35;font-weight:500">Finish today's habits to unlock.</p></div>
<div style="position:absolute;left:200px;top:96px">{sprig(170,'idle')}</div></div>
<div style="position:relative;flex:none;height:52px;margin:-48px -60px -4px;background:#FFF9F1;border-radius:50% 50% 0 0/48px 48px 0 0"></div>
<div style="flex:none;display:flex;justify-content:space-between;align-items:baseline;padding:2px 24px 10px"><h2 style="margin:0;font-size:18px;font-weight:700">Today</h2><p style="margin:0;font-size:14px;font-weight:600;color:#5C5878">2 of 4</p></div>
<div style="flex:none;display:flex;flex-direction:column;gap:8px;padding:0 20px 14px">{hh}</div>
<div style="flex:none;display:flex;justify-content:space-between;align-items:baseline;padding:4px 24px 10px"><h2 style="margin:0;font-size:18px;font-weight:700">This week</h2><p style="margin:0;font-size:14px;font-weight:600;color:#5C5878">Sun to Sat</p></div>
<div style="flex:none;margin:0 20px;padding:16px 16px 14px;border-radius:20px;background:#fff;border:2px solid #EADFD2;display:flex;flex-direction:column;gap:12px">
<div style="display:flex;justify-content:space-between;align-items:baseline"><span style="font-size:17px;font-weight:800">Exercise · 1 of 4 this week</span></div>
<div style="display:flex;gap:6px">{pips}</div>
{weekstrip(['k','t','u','u','u','u','u'],False,28)}
<div style="display:flex;align-items:center;gap:6px;font-size:13px;font-weight:600;color:#218A52"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>On track. 6 days left for 3 more, so no lock.</div></div>
<div style="flex:1"></div>
<nav style="flex:none;height:74px;display:grid;grid-template-columns:repeat(3,1fr);align-items:center;border-top:1.5px solid #EFE4D6;font-size:13px;font-weight:700;text-align:center;color:#5C5878">
<span style="display:flex;flex-direction:column;align-items:center;gap:4px;color:#5B4BD6"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="13" r="4"/><path d="M12 3v2M4.5 6.5l1.5 1.5M19.5 6.5L18 8M3 20h18"/></svg>Today</span>
<span style="display:flex;flex-direction:column;align-items:center;gap:4px"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21v-9M12 12c-4 0-6-2-6-6 4 0 6 2 6 6zM12 15c3.5 0 5.5-2 5.5-5.5-3.5 0-5.5 2-5.5 5.5z"/></svg>Sprig</span>
<span style="display:flex;flex-direction:column;align-items:center;gap:4px"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h10M18 7h2M4 17h2M10 17h10"/><circle cx="16" cy="7" r="2"/><circle cx="8" cy="17" r="2"/></svg>Settings</span></nav>
</div></body></html>'''

for n,h in [('1-weekly-goal-setup',setup),('2-notification',notif),('3-lock-screen',lock),('4-home-weekly-card',home)]:
    open(n+'.html','w').write(h)
