import json,sys,time,collections
from pathlib import Path
sys.stdout.reconfigure(encoding='utf-8')
src=Path(r'C:\Users\javad\Downloads\avapod-87a62-default-rtdb-export.json')
data=json.loads(src.read_text(encoding='utf-8-sig'))
original=json.loads(src.read_text(encoding='utf-8-sig'))
cats=[('جامعه','community'),('جنایی','crime'),('دانش','science'),('ترسناک','horror'),('ادبیات','book'),('سینما','cinema'),('پیشگویی','future'),('فلسفه','thought'),('سیاست','politics'),('ورزش','sport'),('موسیقی','music'),('تاریخ','history'),('روان‌شناسی','psychology'),('فناوری','technology'),('اقتصاد','economy'),('کارآفرینی','business'),('سفر','travel'),('طنز','comedy'),('معنویت','spirituality'),('داستان','audio')]
data['categories']={f'cat_{i:02}':{'name':name,'icon':'ic_category_'+icon} for i,(name,icon) in enumerate(cats,1)}
# Editorial assignments after reading every title/description in the supplied export.
assignments={
13:['دارکست','هلی تاک','جافکری','صلح درون','مدرسه زندگی','رواق'],
17:['رادیو جولون','رادیو چرخ','ولگردی'],
1:['مستی و راستی','مختصر و مفید','رادیو دال','رادیو مرز'],
20:['پادکست آن','چینل بی','رادیو ساگا','فری کست'],
16:['ده صبح','اکنون','کار نکن','قاف پادکست','طبقه ۱۶'],
15:['سکه'],
5:['سبکتو','بی پلاس','کتابگرد','خرمگس'],
2:['پادکست فیکشن'],
18:['رادیو شاخ','طنزپردازی'],
19:['زندگی پس از زندگی','ترجمه ی فارسی قرآن'],
12:['راوکست','پرچم سفید'],
14:['رادیو گیگ','پرامپت'],
11:['مترونوم'],
6:['دیالوگ باکس'],
8:['انسانک']}
by_title={title:f'cat_{cat:02}' for cat,titles in assignments.items() for title in titles}
for pid,podcast in data['podcasts'].items():
 if podcast['title'] in by_title: podcast['category_id']=by_title[podcast['title']]
# Two distinct shows have the title شنود: retain the social conversations, classify the spiritual memoir separately.
data['podcasts']['pod_1780720009441']['category_id']='cat_19'
# Bump snapshot version so clients refresh their local catalogue after import.
data['db_config']['version']=max(int(data['db_config'].get('version',0))+1,int(time.time()*1000))
out=Path('firebase/exports');out.mkdir(parents=True,exist_ok=True)
target=out/'avapod-20-categories.json';target.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
rows=['# دسته‌بندی پادکست‌ها','', 'مبنای بررسی: عنوان و توضیحات موجود در فایل ارسالی؛ محتوای صوتی و RSS دوباره بررسی نشده است. دسته‌های چندموضوعی بر اساس موضوع غالب انتخاب شده‌اند.','', '| پادکست | گوینده | دسته قبلی | دسته جدید |','|---|---|---|---|']
for pid,p in data['podcasts'].items():
 before=original['categories'][original['podcasts'][pid]['category_id']]['name']
 after=data['categories'][p['category_id']]['name']
 rows.append('| '+' | '.join(str(v).replace('|','/') for v in [p['title'],p['artist'],before,after])+' |')
(out/'category-review.md').write_text('\n'.join(rows)+'\n',encoding='utf-8')
assert len(data['categories'])==20
assert len(set(c['name'] for c in data['categories'].values()))==20
assert len(set(c['icon'] for c in data['categories'].values()))==20
assert all(' ' not in c['name'] for c in data['categories'].values())
assert all(Path('app/src/main/res/drawable',c['icon']+'.xml').exists() for c in data['categories'].values())
assert set(data['podcasts'])==set(original['podcasts'])
for pid,p in data['podcasts'].items():
 assert p['category_id'] in data['categories']
 assert {k:v for k,v in p.items() if k!='category_id'}=={k:v for k,v in original['podcasts'][pid].items() if k!='category_id'}
for k in original:
 if k not in ('categories','podcasts','db_config'): assert data[k]==original[k]
assert {k:v for k,v in data['db_config'].items() if k!='version'}=={k:v for k,v in original['db_config'].items() if k!='version'}
changed=sum(p['category_id']!=original['podcasts'][pid]['category_id'] for pid,p in data['podcasts'].items())
print(f'PASS: 20 unique categories/icons; {len(data["podcasts"])} podcasts reviewed, {changed} reassigned; all other fields preserved.')
print(collections.Counter(data['categories'][p['category_id']]['name'] for p in data['podcasts'].values()))
