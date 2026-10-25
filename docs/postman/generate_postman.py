import json
import os
import yaml

SPEC_PATH = 'G:/repos/Finanx_frontend/openapi.yaml'
OUT_DIR = 'G:/repos/finanx/docs/postman'

with open(SPEC_PATH, 'r', encoding='utf-8') as f:
    spec = yaml.safe_load(f)

schemas = spec.get('components', {}).get('schemas', {})


def resolve_ref(ref):
    name = ref.split('/')[-1]
    return schemas.get(name, {})


def example_from_schema(schema, depth=0):
    """Build a JSON-serializable example from an OpenAPI schema."""
    if not isinstance(schema, dict) or depth > 8:
        return {}
    if '$ref' in schema:
        return example_from_schema(resolve_ref(schema['$ref']), depth + 1)
    if 'example' in schema:
        ex = schema['example']
        return str(ex) if not isinstance(ex, (dict, list, int, float, bool, type(None))) else ex
    if 'default' in schema:
        return schema['default']
    typ = schema.get('type')
    if typ == 'object' or 'properties' in schema:
        props = schema.get('properties', {})
        return {k: example_from_schema(v, depth + 1) for k, v in props.items()}
    if typ == 'array':
        item = schema.get('items', {})
        return [example_from_schema(item, depth + 1)]
    if typ in ('integer', 'number'):
        return 0
    if typ == 'boolean':
        return False
    if typ == 'string':
        fmt = schema.get('format')
        if fmt == 'date':
            return '2026-01-01'
        if fmt == 'date-time':
            return '2026-01-01T00:00:00Z'
        return ''
    if 'oneOf' in schema:
        return example_from_schema(schema['oneOf'][0], depth + 1)
    if 'anyOf' in schema:
        return example_from_schema(schema['anyOf'][0], depth + 1)
    if 'allOf' in schema:
        merged = {}
        for part in schema['allOf']:
            value = example_from_schema(part, depth + 1)
            if isinstance(value, dict):
                merged.update(value)
        return merged
    return {}


def build_auth_headers(needs_auth, content_type=None):
    headers = [{"key": "Accept", "value": "application/json", "type": "text"}]
    if content_type and content_type not in ('multipart/form-data',):
        headers.append({"key": "Content-Type", "value": content_type, "type": "text"})
    return headers


def build_url(path, details):
    # Postman supports a variable in host; keep raw as the source of truth.
    raw = "{{base_url}}" + path
    url = {
        "raw": raw,
        "host": ["{{base_url}}"],
        "path": [p for p in path.strip('/').split('/') if p],
        "variable": []
    }
    for param in details.get('parameters', []):
        if param.get('in') == 'path':
            name = param['name']
            url["variable"].append({
                "key": name,
                "value": "{{" + name + "}}",
                "description": param.get('description', '')
            })
    query = []
    for param in details.get('parameters', []):
        if param.get('in') == 'query':
            query.append({
                "key": param['name'],
                "value": "",
                "description": param.get('description', ''),
                "disabled": True
            })
    if query:
        url["query"] = query
    return url


def build_body(details, method):
    if 'requestBody' not in details:
        return None
    content = details['requestBody'].get('content', {})
    if 'application/json' in content:
        schema = content['application/json'].get('schema', {})
        example = example_from_schema(schema)
        return {
            "mode": "raw",
            "raw": json.dumps(example, indent=2, ensure_ascii=False),
            "options": {"raw": {"language": "json"}}
        }
    if 'multipart/form-data' in content:
        formdata = []
        schema = content['multipart/form-data'].get('schema', {})
        if '$ref' in schema:
            schema = resolve_ref(schema['$ref'])
        for prop_name, prop in schema.get('properties', {}).items():
            entry = {
                "key": prop_name,
                "type": "file" if prop.get('type') == 'string' and prop.get('format') == 'binary' else "text",
                "description": prop.get('description', '')
            }
            if entry["type"] == "text":
                entry["value"] = ""
            else:
                entry["src"] = ""
            formdata.append(entry)
        if not formdata:
            formdata.append({"key": "image", "type": "file", "src": ""})
        return {"mode": "formdata", "formdata": formdata}
    return None


def build_tests(tag, path):
    """Token-capturing test scripts for authentication endpoints."""
    scripts = []
    if tag == 'Auth' and path.endswith(('/login', '/register')):
        scripts.append(
            "const json = pm.response.json();\n"
            "const token = json.accessToken || json.access_token || json.token;\n"
            "if (token) {\n"
            "    pm.collectionVariables.set('access_token', token);\n"
            "    console.log('access_token guardado en las variables de coleccion');\n"
            "}\n"
            "if (json.userId) {\n"
            "    pm.collectionVariables.set('userId', String(json.userId));\n"
            "} else if (json.user && json.user.id) {\n"
            "    pm.collectionVariables.set('userId', String(json.user.id));\n"
            "}\n"
            "pm.test('status exitoso', () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));"
        )
    return scripts


collection = {
    "info": {
        "name": "Finanx API",
        "description": (
            "Coleccion completa de endpoints de Finanx API generada desde "
            "`G:/repos/Finanx_frontend/openapi.yaml` (el contrato que consume la app).\n\n"
            "## Uso rapido\n"
            "1. Importa esta coleccion y uno de los entornos de `docs/postman/`.\n"
            "2. Ejecuta `Auth > Login` (o `Registro`) para guardar `access_token` "
            "automaticamente en las variables de coleccion.\n"
            "3. Ajusta `userId`, `walletId`, etc. con tus IDs reales.\n\n"
            "Las rutas ya incluyen el prefijo `/api/v1`. Los endpoints de `Auth` no "
            "envian el Bearer; el resto hereda la auth bearer a nivel de coleccion."
        ),
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    },
    "auth": {
        "type": "bearer",
        "bearer": [{"key": "token", "value": "{{access_token}}", "type": "string"}]
    },
    "variable": [
        {"key": "base_url", "value": "http://127.0.0.1:8082", "type": "string"},
        {"key": "access_token", "value": "", "type": "string"},
        {"key": "userId", "value": "1", "type": "string"},
        {"key": "id", "value": "1", "type": "string"},
        {"key": "walletId", "value": "1", "type": "string"},
        {"key": "transactionId", "value": "1", "type": "string"},
        {"key": "categoryId", "value": "1", "type": "string"},
        {"key": "budgetId", "value": "1", "type": "string"},
        {"key": "savingGoalId", "value": "1", "type": "string"},
        {"key": "receiptId", "value": "1", "type": "string"},
        {"key": "imageId", "value": "1", "type": "string"},
        {"key": "scheduleId", "value": "1", "type": "string"},
        {"key": "sessionId", "value": "1", "type": "string"},
        {"key": "notificationId", "value": "1", "type": "string"},
        {"key": "provider", "value": "stripe", "type": "string"},
        {"key": "health", "value": "OK", "type": "string"}
    ],
    "item": []
}

tags_map = {}
for path, methods in spec.get('paths', {}).items():
    for method, details in methods.items():
        if method not in ('get', 'post', 'put', 'delete', 'patch'):
            continue
        tags = details.get('tags') or ['Other']
        tag = tags[0]
        tags_map.setdefault(tag, []).append((path, method, details))

for tag in sorted(tags_map):
    folder = {"name": tag, "item": []}
    for path, method, details in tags_map[tag]:
        content = details.get('requestBody', {}).get('content', {})
        content_type = next(iter(content), None) if content else None
        needs_auth = tag != 'Auth'

        request = {
            "method": method.upper(),
            "header": build_auth_headers(needs_auth, content_type),
            "url": build_url(path, details),
            "description": details.get('description', details.get('summary', ''))
        }
        if not needs_auth:
            request["auth"] = {"type": "noauth"}

        body = build_body(details, method)
        if body:
            request["body"] = body

        event = build_tests(tag, path)

        item = {
            "name": details.get('summary') or f"{method.upper()} {path}",
            "request": request
        }
        if event:
            item["event"] = [{"listen": "test", "script": {"type": "text/javascript", "exec": event[0].split('\n')}}]
        folder["item"].append(item)
    if folder["item"]:
        collection["item"].append(folder)


def make_env(env_id, name, base_url):
    keys = {
        "base_url": base_url,
        "access_token": "",
        "userId": "1",
        "id": "1",
        "walletId": "1",
        "transactionId": "1",
        "categoryId": "1",
        "budgetId": "1",
        "savingGoalId": "1",
        "receiptId": "1",
        "imageId": "1",
        "scheduleId": "1",
        "sessionId": "1",
        "notificationId": "1",
        "provider": "stripe",
        "health": "OK"
    }
    return {
        "id": env_id,
        "name": name,
        "values": [{"key": k, "value": v, "enabled": True} for k, v in keys.items()],
        "_postman_variable_scope": "environment"
    }


os.makedirs(OUT_DIR, exist_ok=True)
with open(os.path.join(OUT_DIR, 'Finanx_API.postman_collection.json'), 'w', encoding='utf-8') as f:
    json.dump(collection, f, indent=2, ensure_ascii=False)

envs = [
    make_env('finanx-direct-8082', 'Finanx Direct backend (8082)', 'http://127.0.0.1:8082'),
    make_env('finanx-nginx', 'Finanx Docker nginx (80)', 'http://localhost'),
    make_env('finanx-local-8080', 'Finanx Local (8080)', 'http://localhost:8080'),
]
for env in envs:
    filename = env['name'].replace(' ', '_').replace('/', '-').replace('(', '').replace(')', '') + '.postman_environment.json'
    with open(os.path.join(OUT_DIR, filename), 'w', encoding='utf-8') as f:
        json.dump(env, f, indent=2, ensure_ascii=False)

print(f"Coleccion generada con {len(collection['item'])} carpetas y "
      f"{sum(len(f['item']) for f in collection['item'])} peticiones")
print(f"Entornos: {[e['name'] for e in envs]}")
