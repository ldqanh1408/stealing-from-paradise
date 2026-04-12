import os
import re
md_path = r'D:\dev\stealing-from-paradise\BACKEND_CONFIG_MANIFEST.md'
with open(md_path, 'r', encoding='utf-8') as f:
    text = f.read()
# Extract application.yml files
yaml_blocks = re.findall(r'### \d+\.\d+ (?P<title>[^\n]+)\n`yaml\n(?P<content>.*?)\n`', text, re.DOTALL)
for title, content in yaml_blocks:
    service_match = re.search(r'application:\n\s+name:\s+(\S+)', content)
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
            f.write(content.strip() + '\n')
        print(f'Updated {path}')
# Extract pom.xml blocks
pom_blocks = re.findall(r'### \d+\.\d+ [^\n]+\((.*?pom\.xml)\)\s*`xml\n(.*?)\n`', text, re.DOTALL)
for raw_rel, content in pom_blocks:
    file_rel_path = raw_rel.strip()
    if not file_rel_path.startswith('backend/'):
        file_rel_path = 'backend/' + file_rel_path
    path = os.path.join(r'D:\dev\stealing-from-paradise', file_rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content.strip() + '\n')
    print(f'Updated {path}')
