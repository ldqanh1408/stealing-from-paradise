import os
import re
md_path = r'C:\Users\CYBORG\OneDrive\Documents\BACKEND_CONFIG_MANIFEST.md'
with open(md_path, 'r', encoding='utf-8') as f:
    text = f.read()
print("Parsed manifest...")
# Extract Pom
poms = 0
for match in re.finditer(r'### \d+\.\d+ [^\n]+\(([^)]+pom\.xml)\)\s*```xml\s*(.*?)\s*```', text, re.DOTALL):
    file_rel_path = match.group(1).strip()
    content = match.group(2).strip()
    if not file_rel_path.startswith('backend/'):
        file_rel_path = 'backend/' + file_rel_path
    path = os.path.join(r'D:\dev\stealing-from-paradise', file_rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?>\n' + content.replace('<?xml version="1.0" encoding="UTF-8"?>', '').strip() + '\n')
    poms += 1
print(f'Updated {poms} pom.xml files')
# Extract Yaml
yamls = 0
for match in re.finditer(r'### \d+\.\d+ ([^\n]+)\s*```yaml\s*(.*?)\s*```', text, re.DOTALL):
    title = match.group(1).strip()
    content = match.group(2).strip()
    service_match = re.search(r'application:\s*\n\s+name:\s+(\S+)', content)
    if not service_match:
        service_match = re.search(r'application:\s+name:\s+(\S+)', content)
    if service_match:
        name = service_match.group(1)
        if name == 'identity-domain': dir_name = 'identity-service'
        elif name == 'product-domain': dir_name = 'product-service'
        elif name == 'order-domain': dir_name = 'order-service'
        elif name == 'payment-domain': dir_name = 'payment-service'
        else: dir_name = name
        path = os.path.join(r'D:\dev\stealing-from-paradise\backend', dir_name, 'src', 'main', 'resources', 'application.yml')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content + '\n')
        yamls += 1
print(f'Updated {yamls} application.yml files')
# Extract docker-compose
composers = 0
for match in re.finditer(r'### \d+\.\d+.*?\(([^)]*docker-compose\.yml)\)\s*```yaml\s*(.*?)\s*```', text, re.DOTALL):
    rel_path = match.group(1).strip()
    content = match.group(2).strip()
    path = os.path.join(r'D:\dev\stealing-from-paradise', rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content + '\n')
    composers += 1
print(f'Updated {composers} docker-compose.yml files')
# Extract any SQL init files described: Like docker/postgres/init/01-init-db.sql
sqls = 0
for match in re.finditer(r'\*\*`([^`]+\.sql)`\*\*.*?\n```sql\n(.*?)\n```', text, re.DOTALL):
    rel_path = match.group(1).strip()
    content = match.group(2).strip()
    path = os.path.join(r'D:\dev\stealing-from-paradise\backend', rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content + '\n')
    sqls += 1
print(f'Updated {sqls} sql init files')
# Extract any JS init files described: Like docker/mongo/init/01-init-db.js
jss = 0
for match in re.finditer(r'\*\*`([^`]+\.js)`\*\*.*?\n```javascript\n(.*?)\n```', text, re.DOTALL):
    rel_path = match.group(1).strip()
    content = match.group(2).strip()
    path = os.path.join(r'D:\dev\stealing-from-paradise\backend', rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content + '\n')
    jss += 1
print(f'Updated {jss} javascript init files')
