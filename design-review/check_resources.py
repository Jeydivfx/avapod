"""Read-only resource checks; does not invoke Gradle or build an APK."""
from pathlib import Path
import re
import xml.etree.ElementTree as E
root=Path('app/src/main'); res=root/'res'; ids=set(); values={}; errors=[]
xmls=list(res.rglob('*.xml'))
for p in xmls:
 try: tree=E.parse(p)
 except E.ParseError as e: errors.append(f'{p}: {e}'); continue
 for node in tree.iter():
  for v in node.attrib.values():
   if v.startswith('@+id/'): ids.add(v[5:])
  if p.parent.name.startswith('values') and node.get('name'):
   kind=node.tag if node.tag!='item' else node.get('type')
   values.setdefault(kind,set()).add(node.get('name'))
 for parent in tree.iter():
  if parent.tag=='resources':
   names=[(n.tag,n.get('name')) for n in parent if n.get('name')]
   if len(names)!=len(set(names)): errors.append(f'Duplicate value in {p}')
for kind in ['layout','drawable','font','menu','mipmap','raw','xml','color']:
 values.setdefault(kind,set()).update(p.name.split('.')[0] for d in res.glob(kind+'*') for p in d.iterdir() if p.is_file())
values['id']=ids|values.get('id',set())
for p in list(root.rglob('*.kt'))+xmls:
 s=p.read_text(encoding='utf-8')
 if p.parent.name=='raw': continue  # R8 resource keep patterns are not literal references.
 refs=re.findall(r'(?<!android\.)\bR\.(id|string|layout|drawable|color|font|menu|style|dimen)\.([A-Za-z0-9_]+)',s) if p.suffix=='.kt' else re.findall(r'@\+?(id|string|layout|drawable|color|font|menu|style|dimen)/([A-Za-z0-9_.]+)',s)
 for kind,name in refs:
  if (kind,name) in {('style','ThemeOverlay.AppCompat.Dark.ActionBar'),('string','appbar_scrolling_view_behavior')}: continue  # Existing AndroidX/Material resources.
  if name not in values.get(kind,set()): errors.append(f'{p}: missing {kind}/{name}')
# Include-expanded layouts must contain every view required by their controllers.
def layout_ids(name):
 tree=E.parse(res/'layout'/name); result=set()
 for n in tree.iter():
  v=n.get('{http://schemas.android.com/apk/res/android}id','')
  if v.startswith('@+id/'): result.add(v[5:])
  if n.tag=='include': result |= layout_ids(n.get('layout').split('/')[-1]+'.xml')
 return result
checks={
 'fragment_admin_dashboard.xml':['admin_search','admin_search_empty','rv_admin_podcasts','btn_add_new_podcast','btn_back_common','btn_menu_common'],
 'fragment_home.xml':['catalog_empty_panel','btn_retry_catalog','swipe_refresh_layout','shimmer_trending','progress_loading'],
 'fragment_about.xml':['btn_back_common','btn_menu_common','txt_shamsi','txt_miladi','txt_daily_message','app_version_text','txt_email'],
 'activity_podcast_detail.xml':['btn_back_common','btn_menu_common','rv_episodes','img_full_cover','btn_subscribe','btn_report_podcast','txt_podcast_title','txt_podcast_artist','txt_podcast_description','loader','btn_description_more','episode_error_panel','episode_error_message','btn_retry_episodes'],
 'fragment_library.xml':['library_new_episodes','library_downloads','library_bookmarks','library_history','rv_library','txt_empty_library'],
 'fragment_search_results.xml':['search_query_input','rv_search_results','txt_no_result','loader'],
 'item_episode.xml':['txt_episode_title','txt_episode_time','txt_episode_date','img_episode','progress_listening','txt_percent']}
for layout,needed in checks.items():
 for id in needed:
  if id not in layout_ids(layout): errors.append(f'{layout}: missing required view {id}')
print(f'Parsed {len(xmls)} XML files; checked resource references and {len(checks)} critical view contracts.')
for e in errors: print(e)
assert not errors, f'{len(errors)} resource errors'
print('PASS — static checks only; not an Android build or runtime test.')
