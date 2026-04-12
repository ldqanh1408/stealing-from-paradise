import os
import re
md_path = r'D:\dev\stealing-from-paradise\BACKEND_CONFIG_MANIFEST.md'
with open(md_path, 'r', encoding='utf-8') as f:
    text = f.read()
# Extract Pom
for match in re.finditer(r'### \d+\.\d+ [^\n]+\(([^)]+pom\.xml)\)\s*`xml\s*(.*?)\s*`', text, re.DOTALL):
    file_rel_path = match.group(1).strip()
    content = match.group(2).strip()
    if not file_rel_path.startswith('backend/'):
        file_rel_path = 'backend/' + file_rel_path
    path = os.path.join(r'D:\dev\stealing-from-paradise', file_rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content + '\n')
    print(f'Updated {path}')
# Extract Yaml
for match in re.finditer(r'### \d+\.\d+ ([^\n]+)\s*`yaml\s*(.*?)\s*`', text, re.DOTALL):
    title = match.group(1).strip()
    content = match.group(2).strip()
    service_match = re.search(r'application:\s+name:\s+(\S+)', content)
    if not service_match:
        service_match = re.search(r'application:\n\s+name:\s+(\S+)', content)
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
        print(f'Updated {path}')
