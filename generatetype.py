import os
import re
from collections import defaultdict
import argparse
import shutil

# --- CONFIGURATION ---
DEFAULT_SOURCE_DIRS = [
    'api/src/main/java',
    'client-mod/src/main/java',
    'network-engine/src/main/java',
    'server/src/main/java'
]
DEFAULT_DTS_FILE = 'packages/sdk/src/index.ts'

# --- REGEX PATTERNS ---
JAVA_CLASS_PATTERN = re.compile(r'public (?:final |abstract )?(?:class|record|interface) (\w+)')
JAVA_MEMBER_PATTERN = re.compile(
    r'@HostAccess\.Export\s+'
    r'(?:public\s+)?'
    r'([\w.<>\[\]?]+)\s+'  # Group 1: Return type
    r'(\w+)\s*'           # Group 2: Member name
    r'(\(.*\)|;)'         # Group 3: Parameters or semicolon
)
TS_BLOCK_PATTERN = re.compile(
    r'(?:export\s+)?(?:declare\s+)?(interface|class)\s+([\w]+)?\s*.*?\{([\s\S]*?)\}',
    re.MULTILINE
)
TS_MEMBER_PATTERN = re.compile(r'^\s*(?:readonly\s+)?(\w+)\s*[:(]', re.MULTILINE)

def find_java_exports(source_dirs):
    """Scans Java source files for @HostAccess.Export members, including their types."""
    java_exports = defaultdict(dict)
    print("🔍 Scanning Java source files...")

    for src_dir in source_dirs:
        if not os.path.isdir(src_dir):
            print(f"⚠️  Warning: Source directory not found, skipping: {src_dir}")
            continue
        for root, _, files in os.walk(src_dir):
            for file in files:
                if file.endswith('.java'):
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, 'r', encoding='utf-8') as f:
                            content = f.read()
                            class_match = JAVA_CLASS_PATTERN.search(content)
                            if class_match:
                                class_name = class_match.group(1)
                                for match in JAVA_MEMBER_PATTERN.finditer(content):
                                    return_type, member_name, params = match.groups()
                                    is_method = params.startswith('(')
                                    java_exports[class_name][member_name] = (return_type, is_method)
                    except Exception as e:
                        print(f"⚠️  Could not read file {file_path}: {e}")

    print(f"✅ Found {sum(len(v) for v in java_exports.values())} exported members in {len(java_exports)} Java classes.")
    return java_exports

def find_ts_declarations(dts_file):
    """Parses a .d.ts file to find all declared interfaces/classes and their members."""
    ts_declarations = defaultdict(set)
    print(f"🔍 Parsing TypeScript declaration file: {dts_file}...")

    try:
        with open(dts_file, 'r', encoding='utf-8') as f:
            content = f.read()
            # Handle global scope
            global_match = re.search(r'declare global\s*\{([\s\S]*?)\}', content, re.MULTILINE)
            search_content = content + (global_match.group(1) if global_match else "")

            for _, interface_name, body in TS_BLOCK_PATTERN.findall(search_content):
                members = TS_MEMBER_PATTERN.findall(body)
                ts_declarations[interface_name].update(members)

        print(f"✅ Found {sum(len(v) for v in ts_declarations.values())} declared members in {len(ts_declarations)} TypeScript interfaces/classes.")
        return ts_declarations
    except FileNotFoundError:
        print(f"❌ Error: Declaration file not found at '{dts_file}'.")
        return None

def guess_ts_type(java_type, member_name):
    """Guesses a basic TypeScript type from a Java type and member name."""
    if member_name.startswith('is') or member_name.startswith('has'):
        return 'boolean'
    if java_type in ['void', 'Void']:
        return 'void'
    if java_type in ['String']:
        return 'string'
    if java_type.lower() in ['int', 'integer', 'long', 'float', 'double', 'byte', 'short', 'number']:
        return 'number'
    if java_type.lower() in ['boolean']:
        return 'boolean'
    if 'Vector3' in java_type:
        return 'Vector3'
    if 'Quaternion' in java_type:
        return 'Quaternion'
    if 'ProxyArray' in java_type:
        return 'any[]'
    # Default fallback
    return 'any'

def generate_ts_member_string(member_name, java_type, is_method):
    """Generates a TypeScript member declaration string."""
    ts_type = guess_ts_type(java_type, member_name)
    if is_method:
        # We can add a basic JSDoc comment for clarity
        return f"    /**\n     * Auto-generated from Java method '{member_name}'.\n     * Please specify parameters and update return type if necessary.\n     */\n    {member_name}(...args: any[]): {ts_type};"
    else:
        return f"    readonly {member_name}: {ts_type};"

def update_dts_file(dts_file, missing_declarations):
    """Updates the .d.ts file with missing declarations."""
    if not missing_declarations:
        print("\n✅ No updates needed for the .d.ts file.")
        return

    backup_file = dts_file + '.bak'
    print(f"\n💾 Creating backup: {backup_file}")
    shutil.copy(dts_file, backup_file)

    with open(dts_file, 'r+', encoding='utf-8') as f:
        content = f.read()

        new_interfaces_to_add = []

        for key, members in missing_declarations.items():
            class_info = dict(key)
            java_class_name = class_info['java']
            ts_interface_name = class_info['ts']

            # Generate member strings
            member_strings = [generate_ts_member_string(name, rtype, is_method) for name, (rtype, is_method) in members.items()]

            # Find the interface/class in the content
            # Regex to find the whole block, including potential 'export declare'
            interface_regex = re.compile(
                r'((?:export\s+)?(?:declare\s+)?(?:interface|class)\s+' + re.escape(ts_interface_name) + r'\s*.*?\{)',
                re.MULTILINE | re.DOTALL
            )
            match = interface_regex.search(content)

            if match:
                # Interface exists, inject new members before the closing brace
                print(f"✍️  Updating existing interface '{ts_interface_name}'...")
                # Find the position of the last '}' for the matched block
                start_pos = match.start()
                open_braces = 0
                insert_pos = -1
                for i in range(start_pos, len(content)):
                    if content[i] == '{':
                        open_braces += 1
                    elif content[i] == '}':
                        open_braces -= 1
                        if open_braces == 0:
                            insert_pos = i
                            break

                if insert_pos != -1:
                    new_body = "\n" + "\n".join(member_strings) + "\n"
                    content = content[:insert_pos] + new_body + content[insert_pos:]
                else:
                    print(f"⚠️ Could not find closing brace for '{ts_interface_name}'. Appending as new interface.")
                    # Fallback to creating a new interface if parsing fails
                    new_interfaces_to_add.append(create_new_interface_string(ts_interface_name, java_class_name, member_strings))

            else:
                # Interface does not exist, queue it for addition at the end
                print(f"✨ Creating new interface '{ts_interface_name}' for Java class '{java_class_name}'...")
                new_interfaces_to_add.append(create_new_interface_string(ts_interface_name, java_class_name, member_strings))

        # Append any brand new interfaces to the end of the file
        if new_interfaces_to_add:
            if not content.strip().endswith("}"):
                content += "\n"
            content += "\n// --- Auto-generated interfaces ---\n"
            content += "".join(new_interfaces_to_add)

        # Write the updated content back to the file
        f.seek(0)
        f.write(content)
        f.truncate()

    print(f"\n🎉 Successfully updated '{dts_file}'. Review the changes and adjust types as needed.")

def create_new_interface_string(ts_name, java_name, member_strings):
    """Helper to format a new interface string."""
    interface_str = f"\n/**\n * Auto-generated for Java class `{java_name}`.\n */\ndeclare interface {ts_name} {{\n"
    interface_str += "\n".join(member_strings)
    interface_str += "\n}\n"
    return interface_str

def main():
    parser = argparse.ArgumentParser(description="Generate and update TypeScript declarations for exported Java members.")
    parser.add_argument(
        '-s', '--source',
        nargs='+',
        default=DEFAULT_SOURCE_DIRS,
        help=f"List of Java source directories to scan. Default: {' '.join(DEFAULT_SOURCE_DIRS)}"
    )
    parser.add_argument(
        '-d', '--dts',
        default=DEFAULT_DTS_FILE,
        help=f"Path to the main TypeScript declaration file. Default: {DEFAULT_DTS_FILE}"
    )

    args = parser.parse_args()

    java_exports = find_java_exports(args.source)
    ts_declarations = find_ts_declarations(args.dts)

    if ts_declarations is None:
        return

    # This will store the final data structure needed for the update function.
    # Format: { class_info_key (tuple): { member_name: (return_type, is_method) } }
    missing_declarations = defaultdict(dict)
    total_missing_count = 0

    for java_class in sorted(java_exports.keys()):
        # Determine the corresponding TypeScript interface name
        ts_name = java_class.replace("Proxy", "")
        if ts_name not in ts_declarations and java_class in ts_declarations:
            ts_name = java_class

        class_info = {'java': java_class, 'ts': ts_name}

        ts_decls_set = ts_declarations.get(ts_name, set())

        for member_name, (return_type, is_method) in java_exports[java_class].items():
            if member_name not in ts_decls_set:
                # Use a tuple of the dict's items as a hashable key
                class_info_key = tuple(sorted(class_info.items()))
                missing_declarations[class_info_key][member_name] = (return_type, is_method)
                total_missing_count += 1

    if total_missing_count > 0:
        print(f"\n❌ Found {total_missing_count} total missing members. Attempting to add them...")
        update_dts_file(args.dts, missing_declarations)
    else:
        print("\n🎉 Excellent! All exported Java members are declared in your TypeScript definitions.")


if __name__ == "__main__":
    main()