#!/usr/bin/env python3
"""
IRO Studio 3.0 - Multi-Source Automated Film Recipe Synchronizer
Crawls and parses film simulation recipes from:
  1. Fuji X Weekly (Official public blog & RSS)
  2. FujiStyle (fujistyleapp.com community library)
Can be executed manually or scheduled via systemd timer.
"""

import subprocess
import xml.etree.ElementTree as ET
import json
import re
import html
import hashlib
import os
import sys
import datetime

RECIPES_FILE = '/var/www/iro-studio/frontend/recipes.json'
BACKUP_DIR = '/var/www/iro-studio/backend/data/recipe_backups'
LOG_FILE = '/var/log/iro-recipe-sync.log'

KNOWN_FILM_SIMS = [
    'Classic Chrome', 'Provia', 'Velvia', 'Astia', 'Classic Neg',
    'Classic Negative', 'Nostalgic Neg', 'Nostalgic Negative',
    'Eterna', 'Eterna Bleach Bypass', 'Reala Ace', 'Acros',
    'Acros+R', 'Acros+G', 'Acros+Ye', 'Monochrome', 'Monochrome+R',
    'Monochrome+G', 'Monochrome+Ye', 'Pro Neg. Hi', 'Pro Neg Hi',
    'Pro Neg. Std', 'Pro Neg Std', 'Sepia'
]

SENSOR_NAMES = [
    'X-Trans V', 'X-Trans IV', 'X-Trans III', 'X-Trans II', 'X-Trans I',
    'GFX', 'Bayer'
]

def log(msg):
    ts = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    line = f"[{ts}] {msg}"
    print(line)
    try:
        with open(LOG_FILE, 'a', encoding='utf-8') as f:
            f.write(line + '\n')
    except Exception:
        pass

def fetch_with_curl(url, timeout=20):
    try:
        res = subprocess.run([
            'curl', '-s', '-L',
            '-H', 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
            '-H', 'Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            '--max-time', str(timeout),
            url
        ], capture_output=True, timeout=timeout+5)
        return res.stdout
    except Exception as e:
        log(f"Curl error for {url}: {e}")
        return b''

def clean_recipe_name(raw_title):
    t = raw_title.strip()
    t = re.sub(r'^(?:New:?\s*|Fujifilm\s+Recipe:?\s*|Fujifilm\s+Film\s+Simulation\s+Recipe:?\s*)', '', t, flags=re.I)
    t = re.sub(r'\s*[—–-]\s*(?:A\s+)?Fujifilm\s+Recipe.*$', '', t, flags=re.I)
    t = re.sub(r'\s*[—–-]\s*Film\s+Simulation\s+Recipe.*$', '', t, flags=re.I)
    t = re.sub(r'\s*\(Fujifilm.*?\)$', '', t, flags=re.I)
    t = re.sub(r'&#8217;', "'", t)
    t = re.sub(r'&amp;', "&", t)
    return t.strip()

def detect_sensors(text):
    found = []
    for s in SENSOR_NAMES:
        if re.search(r'\b' + re.escape(s) + r'\b', text, re.I):
            found.append(s.upper().replace(' ', '-'))
    return found if found else ['X-TRANS']

# =========================================================================
# FUJISTYLE PARSER
# =========================================================================
def parse_fujistyle_recipe(url, raw_html):
    title_m = re.search(r'\\?"title\\?":\s*\\?"([^"\\]+)\\?"', raw_html)
    if not title_m:
        title_m = re.search(r'<title>([^<]+?)(?:[–—|].*)?</title>', raw_html, re.I)

    film_sim_m = re.search(r'\\?"filmSimulationName\\?":\s*\\?"([^"\\]+)\\?"', raw_html)
    sensor_m = re.search(r'\\?"sensorType\\?":\s*\\?"([^"\\]+)\\?"', raw_html)
    middle_img_m = re.search(r'\\?"middleImage\\?":\s*\\?"([^"\\]+)\\?"', raw_html)
    thumb_img_m = re.search(r'\\?"thumbImage\\?":\s*\\?"([^"\\]+)\\?"', raw_html)

    if not title_m:
        return None

    title = title_m.group(1).strip()
    film_sim = film_sim_m.group(1).strip() if film_sim_m else 'Classic Chrome'
    sensor_str = sensor_m.group(1).strip() if sensor_m else 'X-Trans IV'
    img_url = middle_img_m.group(1).strip() if middle_img_m else (thumb_img_m.group(1).strip() if thumb_img_m else None)

    items = re.findall(r'\\?"label\\?":\s*\\?"([^"\\]+)\\?",\s*\\?"value\\?":\s*\\?"([^"\\]+)\\?"', raw_html)
    if len(items) < 4:
        return None

    raw_settings = {}
    wb_base = ''
    wb_r = '0'
    wb_b = '0'
    for label, val in items:
        if label == 'White Balance':
            wb_base = val
        elif label == 'WB Shift Red':
            wb_r = val
        elif label == 'WB Shift Blue':
            wb_b = val
        elif label in ['Film Simulation', 'Dynamic Range', 'Highlight', 'Shadow', 'Color', 'Sharpness', 'Noise Reduction', 'Clarity', 'Grain Effect', 'Grain Size', 'Color Chrome Effect', 'Color Chrome FX Blue', 'ISO']:
            raw_settings[label] = val

    if not raw_settings.get('Film Simulation'):
        raw_settings['Film Simulation'] = film_sim

    wb_str = wb_base or 'Auto'
    if wb_r != '0' or wb_b != '0':
        r_sgn = '+' if not wb_r.startswith('-') else ''
        b_sgn = '+' if not wb_b.startswith('-') else ''
        wb_str = f"{wb_base}, {r_sgn}{wb_r} Red & {b_sgn}{wb_b} Blue"
    raw_settings['White Balance'] = wb_str

    sensors = [sensor_str.upper().replace(' ', '-')] if sensor_str and sensor_str != '$undefined' else ['X-TRANS']

    recipe_id = 'fs_' + hashlib.md5(url.encode('utf-8')).hexdigest()[:13]

    return {
        'id': recipe_id,
        'name': f"[FujiStyle] {title}",
        'source': 'FujiStyle Community',
        'sourceUrl': url,
        'sensors': sensors,
        'film': film_sim,
        'rawSettings': raw_settings,
        'reviewRequired': False,
        'missingFields': [],
        'sampleAsset': None,
        'sampleImageUrl': img_url,
        'sampleImages': [img_url] if img_url else [],
        'isNew': True,
        'addedDate': datetime.datetime.now().strftime('%Y-%m-%d')
    }

# =========================================================================
# FUJI X WEEKLY PARSER
# =========================================================================
def parse_fuji_x_weekly_recipe(article_url, title, html_body):
    text_content = html.unescape(re.sub(r'<[^>]+>', '\n', html_body))
    lines = [line.strip() for line in text_content.split('\n') if line.strip()]

    img_matches = re.findall(r'<img[^>]+src=["\'](https?://[^"\']+\.(?:jpg|jpeg|png|webp)[^"\']*)["\']', html_body, re.I)
    sample_images = []
    for img_url in img_matches:
        img_clean = img_url.split('?')[0] if '?' in img_url and 'staticflickr' not in img_url and 'wp.com' not in img_url else img_url
        if any(ign in img_clean.lower() for ign in ['avatar', 'gravatar', 'logo', 'icon', 'pixel.wp.com', 'header', 'screenshot']):
            continue
        if img_clean not in sample_images:
            sample_images.append(img_clean)

    sample_image = sample_images[0] if sample_images else None

    param_map = {}
    keys = [
        'Film Simulation', 'Dynamic Range', 'Grain Effect', 'Color Chrome Effect',
        'Color Chrome FX Blue', 'White Balance', 'Highlight', 'Shadow',
        'Color', 'Sharpness', 'Noise Reduction', 'High ISO NR', 'Clarity',
        'ISO', 'Exposure Compensation'
    ]

    for line in lines:
        for k in keys:
            pattern = rf'^{k}[\s:]+(.+)$'
            m = re.match(pattern, line, re.I)
            if m and k not in param_map:
                candidate_val = m.group(1).strip()
                if not any(bad in candidate_val.lower() for bad in ['#', ';', '{', 'px', 'http', 'width:']):
                    param_map[k] = candidate_val

    if 'Noise Reduction' not in param_map and 'High ISO NR' in param_map:
        param_map['Noise Reduction'] = param_map['High ISO NR']

    core_keys = ['Dynamic Range', 'White Balance', 'Highlight', 'Shadow', 'Color']
    matched_core = [k for k in core_keys if k in param_map]
    if len(matched_core) < 2:
        return None

    film = param_map.get('Film Simulation', '')
    if not film or film.lower() == 'recipes':
        found_sim = None
        for kf in KNOWN_FILM_SIMS:
            if re.search(r'\b' + re.escape(kf) + r'\b', html_body, re.I):
                found_sim = kf
                break
        film = found_sim or 'Classic Chrome'
    param_map['Film Simulation'] = film

    recipe_name = clean_recipe_name(title)
    if not recipe_name or len(recipe_name) < 3 or 'how to program' in recipe_name.lower():
        return None

    recipe_id = hashlib.md5(article_url.encode('utf-8')).hexdigest()[:16]

    return {
        'id': recipe_id,
        'name': recipe_name,
        'source': 'Fuji X Weekly',
        'sourceUrl': article_url,
        'sensors': detect_sensors(html_body),
        'film': film,
        'rawSettings': param_map,
        'reviewRequired': False,
        'missingFields': [],
        'sampleAsset': None,
        'sampleImageUrl': sample_image,
        'sampleImages': sample_images[:4],
        'isNew': True,
        'addedDate': datetime.datetime.now().strftime('%Y-%m-%d')
    }

# =========================================================================
# MAIN SYNC WORKFLOW
# =========================================================================
def sync_recipes(force_restore_base=False):
    log("=== Bắt đầu đồng bộ kho công thức màu phim đa nguồn (Fuji X Weekly + FujiStyle) ===")
    if not os.path.exists(RECIPES_FILE):
        log(f"Lỗi: Không tìm thấy tệp {RECIPES_FILE}")
        return 0

    pristine_backup = os.path.join(BACKUP_DIR, 'recipes_20261018_204817.json')
    if force_restore_base and os.path.exists(pristine_backup):
        with open(pristine_backup, 'r', encoding='utf-8') as f:
            catalog = json.load(f)
        log("Đã tải lại catalog gốc từ bản sao lưu ban đầu.")
    else:
        with open(RECIPES_FILE, 'r', encoding='utf-8') as f:
            catalog = json.load(f)

    existing_recipes = catalog.get('recipes', [])
    existing_urls = set()
    for r in existing_recipes:
        if r.get('sourceUrl'):
            existing_urls.add(r['sourceUrl'].strip().rstrip('/'))
        if r.get('id'):
            existing_urls.add(r['id'])

    log(f"Đã nạp {len(existing_recipes)} công thức hiện có trong thư viện.")

    added_recipes = []

    # -------------------------------------------------------------------------
    # 1. Quét FujiStyle Community (fujistyleapp.com)
    # -------------------------------------------------------------------------
    log(">>> Bắt đầu quét nguồn FUJISTYLE (fujistyleapp.com)...")
    fujistyle_urls = set()

    # Quét Sitemap
    try:
        sitemap_raw = fetch_with_curl('https://www.fujistyleapp.com/sitemap.xml').decode('utf-8', errors='ignore')
        sm_links = re.findall(r'<loc>(https://www\.fujistyleapp\.com/fujifilm-recipes/[^<]+)</loc>', sitemap_raw)
        for link in sm_links:
            clean = link.strip().rstrip('/')
            if clean and clean != 'https://www.fujistyleapp.com/fujifilm-recipes':
                fujistyle_urls.add(clean + '/')
        log(f"Tìm thấy {len(fujistyle_urls)} liên kết công thức từ sitemap FujiStyle.")
    except Exception as e:
        log(f"Lỗi khi đọc sitemap FujiStyle: {e}")

    # Quét thêm từ trang chủ và danh mục
    try:
        home_raw = fetch_with_curl('https://www.fujistyleapp.com/fujifilm-recipes/').decode('utf-8', errors='ignore')
        home_links = re.findall(r'href=["\'](/fujifilm-recipes/[^"\'/]+/)["\']', home_raw)
        for l in home_links:
            full = 'https://www.fujistyleapp.com' + l
            fujistyle_urls.add(full)
    except Exception as e:
        log(f"Lỗi khi quét danh mục FujiStyle: {e}")

    fs_added = 0
    for fs_url in fujistyle_urls:
        clean_url = fs_url.rstrip('/')
        if clean_url in existing_urls:
            continue
        try:
            raw_page = fetch_with_curl(fs_url).decode('utf-8', errors='ignore')
            fs_rec = parse_fujistyle_recipe(fs_url, raw_page)
            if fs_rec:
                log(f"  + [FujiStyle] Đã trích xuất: '{fs_rec['name']}' ({fs_rec['film']})")
                added_recipes.append(fs_rec)
                existing_urls.add(clean_url)
                fs_added += 1
        except Exception as e:
            log(f"Lỗi khi trích xuất FujiStyle {fs_url}: {e}")

    log(f"Hoàn thành quét FujiStyle: Thêm mới {fs_added} công thức.")

    # -------------------------------------------------------------------------
    # 2. Quét Fuji X Weekly (fujixweekly.com)
    # -------------------------------------------------------------------------
    log(">>> Bắt đầu quét nguồn FUJI X WEEKLY...")
    fxw_articles = []
    try:
        xml_data = fetch_with_curl('https://fujixweekly.com/feed/')
        if xml_data:
            root = ET.fromstring(xml_data)
            for it in root.findall('.//item'):
                link = it.find('link').text.strip() if it.find('link') is not None else ''
                clean_link = link.rstrip('/')
                if clean_link and clean_link not in existing_urls:
                    title = it.find('title').text.strip() if it.find('title') is not None else ''
                    content_elem = it.find('{http://purl.org/rss/1.0/modules/content/}encoded')
                    content_html = content_elem.text if content_elem is not None else ''
                    fxw_articles.append((link, title, content_html))
    except Exception as e:
        log(f"Lỗi quét feed Fuji X Weekly: {e}")

    fxw_added = 0
    for link, title, content_html in fxw_articles:
        clean_link = link.rstrip('/')
        if clean_link in existing_urls:
            continue
        try:
            if len(content_html) < 200:
                content_html = fetch_with_curl(link).decode('utf-8', errors='ignore')
            rec = parse_fuji_x_weekly_recipe(link, title, content_html)
            if rec:
                log(f"  + [Fuji X Weekly] Đã trích xuất: '{rec['name']}' ({rec['film']})")
                added_recipes.append(rec)
                existing_urls.add(clean_link)
                fxw_added += 1
        except Exception as e:
            log(f"Lỗi khi xử lý Fuji X Weekly {link}: {e}")

    log(f"Hoàn thành quét Fuji X Weekly: Thêm mới {fxw_added} công thức.")

    # -------------------------------------------------------------------------
    # Lưu và cập nhật kho dữ liệu
    # -------------------------------------------------------------------------
    total_added = len(added_recipes)
    if total_added > 0:
        os.makedirs(BACKUP_DIR, exist_ok=True)
        backup_file = os.path.join(BACKUP_DIR, f"recipes_{datetime.datetime.now().strftime('%Y%m%d_%H%M%S')}.json")
        with open(backup_file, 'w', encoding='utf-8') as f:
            json.dump(catalog, f, ensure_ascii=False, indent=2)
        log(f"Đã sao lưu kho màu cũ vào: {backup_file}")

        # Đưa công thức mới lên đầu danh sách
        catalog['recipes'] = added_recipes + existing_recipes
        catalog['recipeCount'] = len(catalog['recipes'])
        catalog['retrievedOn'] = datetime.datetime.now().strftime('%Y-%m-%d')
        catalog['lastSync'] = datetime.datetime.now().isoformat()
        catalog['sources'] = ['Fuji X Weekly', 'FujiStyle Community']

        tmp_file = RECIPES_FILE + '.tmp'
        with open(tmp_file, 'w', encoding='utf-8') as f:
            json.dump(catalog, f, ensure_ascii=False, indent=2)
        os.replace(tmp_file, RECIPES_FILE)

        log(f"TỔNG KẾT: Đã bổ sung thành công {total_added} công thức màu mới (FujiStyle: {fs_added}, Fuji X Weekly: {fxw_added})!")
        log(f"Tổng số công thức hiện có trong thư viện: {catalog['recipeCount']}")
    else:
        log("Kho công thức đã ở trạng thái mới nhất. Không có công thức mới cần bổ sung.")

    return total_added

if __name__ == '__main__':
    force_restore = '--restore' in sys.argv
    sync_recipes(force_restore_base=force_restore)
