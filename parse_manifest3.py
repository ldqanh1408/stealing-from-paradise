import os
import re
md_path = r'D:\dev\stealing-from-paradise\BACKEND_CONFIG_MANIFEST.md'
with open(md_path, 'r', encoding='utf-8') as f:
    text = f.read()
# For POMS: Look for (backend/pom.xml) etc
for match in re.finditer(r'### [^\n]*?\((.*?pom\.xml)\)[^\n]*\n+`xml\n(.*?)\n`', text, re.DOTALL):
    rel_path = match.group(1).strip()
    content = match.group(2).strip()
    if not rel_path.startswith('backend/'):
        rel_path = 'backend/' + rel_path
    path = os.path.join(r'D:\dev\stealing-from-paradise', rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content + '\n')
    print(f'Updated {path}')
# For YAMLS (Application config)
for match in re.finditer(r'### \d+\.\d+ ([^\n]+)\n+`yaml\n(.*?)\n`', text, re.DOTALL):
    title = match.group(1).strip()
    content = match.group(2).strip()
    # Try to find the service name
    service_match = re.search(r'application:\s*\n\s*name:\s*(\S+)', content)
    if not service_match:
        service_match = re.search(r'application:\s*name:\s*(\S+)', content)
    if service_match:
        name = service_match.group(1)
        dir_name = name
        if name == 'identity-domain': dir_name = 'identity-service'
        elif name == 'product-domain': dir_name = 'product-service'
        elif name == 'order-domain': dir_name = 'order-service'
        elif name == 'payment-domain': dir_name = 'payment-service'
        path = os.path.join(r'D:\dev\stealing-from-paradise\backend', dir_name, 'src', 'main', 'resources', 'application.yml')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content + '\n')
        print(f'Updated {path}')
