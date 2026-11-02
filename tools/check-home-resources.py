"""Static resource/lookup checks only; does not compile or launch Android."""
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
ANDROID = '{http://schemas.android.com/apk/res/android}'
resources = {}
for path in RES.rglob('*'):
    if not path.is_file():
        continue
    kind = path.parent.name.split('-')[0]
    if kind != 'values':
        resources.setdefault(kind, set()).add(path.stem)
    if path.suffix != '.xml':
        continue
    tree = ET.parse(path)
    if kind == 'values':
        for node in tree.getroot():
            resources.setdefault(node.tag, set()).add(node.get('name'))
    for node in tree.iter():
        value = node.get(ANDROID + 'id', '')
        if value.startswith('@+id/'):
            resources.setdefault('id', set()).add(value[5:])

files = [RES / 'layout' / name for name in (
    'fragment_home.xml', 'fragment_home_collection.xml', 'item_home_episode.xml',
    'item_home_hero.xml', 'item_admin_podcast.xml')]
for path in files:
    for kind, name in re.findall(r'@(?!android:|\+)(\w+)/(\w+)', path.read_text('utf-8')):
        assert name in resources.get(kind, set()), (path, kind, name)

java = ROOT / 'app/src/main/java/com/avapod/app'
for path in java.rglob('*.kt'):
    if path.name not in {'HomeFragment.kt', 'HomeCollectionFragment.kt', 'AdminEpisodePicker.kt',
                         'HomeHeroAdapter.kt', 'HomeShelfAdapter.kt', 'AdminPodcastAdapter.kt'}:
        continue
    for kind, name in re.findall(r'(?<!android\.)R\.(\w+)\.(\w+)', path.read_text('utf-8')):
        assert name in resources.get(kind, set()), (path, kind, name)

contracts = {'fragment_home.xml': ['rv_home_hero', 'home_hero_dots', 'rv_home_latest',
                                 'rv_trending', 'home_top_status', 'home_latest_status',
                                 'btn_all_latest', 'btn_all_recommended', 'search_bar'],
             'item_home_episode.xml': ['home_cover_frame', 'home_card_title', 'home_card_artist',
                                       'home_card_cover', 'home_card_duration'],
             'item_home_hero.xml': ['hero_title', 'hero_description', 'hero_cover', 'hero_listen', 'hero_listen_surface'],
             'item_admin_podcast.xml': ['btn_admin_episodes', 'btn_edit_admin', 'btn_delete_admin']}
for name, required in contracts.items():
    ids = {n.get(ANDROID + 'id') for n in ET.parse(RES / 'layout' / name).iter()}
    assert all('@+id/' + i in ids for i in required), name
rules = json.loads((ROOT / 'firebase/database.rules.json').read_text('utf-8'))['rules']
assert rules['.write'] is False
assert rules['home_content']['top_episodes']['.write'] == 'auth != null && auth.token.admin === true'
print('PASS: XML parsing, new resource references, view IDs and admin-only rule declaration.')
print('Not a build, Firebase rule emulator test or device UI test.')
