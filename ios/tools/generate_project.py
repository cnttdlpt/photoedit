#!/usr/bin/env python3
"""Deterministic, dependency-free Xcode project generator. Run after adding Swift source files."""
import hashlib
import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'IRO' if (ROOT / 'IRO').is_dir() else ROOT.parent / 'IRO'
if not SOURCE.is_dir():
    raise SystemExit('Không tìm thấy thư mục nguồn IRO cạnh project hoặc tại thư mục gốc.')
objects = {}
existing_objects = {}
existing_project = ROOT / 'IRO.xcodeproj/project.pbxproj'
if existing_project.exists():
    existing_objects = json.loads(subprocess.check_output(
        ['plutil', '-convert', 'json', '-o', '-', str(existing_project)]))['objects']


def ident(key):
    return hashlib.sha1(key.encode()).hexdigest()[:24].upper()


def quote(value):
    return json.dumps(str(value), ensure_ascii=False)


def add(key, body):
    uid = ident(key)
    objects[uid] = body
    return uid


def array(items):
    return '(' + ', '.join(items) + (',' if items else '') + ')'


def build_file(path, kind):
    relative = os.path.relpath(path, ROOT)
    file_type = {'swift': 'sourcecode.swift', 'json': 'text.json', 'xcassets': 'folder.assetcatalog', 'xcprivacy': 'text.xml'}[path.suffix[1:]]
    ref = add(relative, f'isa = PBXFileReference; lastKnownFileType = {file_type}; path = {quote(relative)}; sourceTree = SOURCE_ROOT;')
    build = add('build:' + relative, f'isa = PBXBuildFile; fileRef = {ref};')
    return ref, build


app_sources = sorted(SOURCE.rglob('*.swift'))
test_sources = sorted((ROOT / 'Tests').glob('*Tests.swift'))
resources = [SOURCE / 'Resources' / p for p in ['recipes.json', 'sample-recipe.json', 'Assets.xcassets', 'PrivacyInfo.xcprivacy']]
app_files = [build_file(p, 'app') for p in app_sources]
tests = [build_file(p, 'test') for p in test_sources]
assets = [build_file(p, 'resource') for p in resources]
app_product = add('IRO.app', 'isa = PBXFileReference; explicitFileType = wrapper.application; path = IRO.app; sourceTree = BUILT_PRODUCTS_DIR;')
test_product = add('IROTests.xctest', 'isa = PBXFileReference; explicitFileType = wrapper.cfbundle; path = IROTests.xctest; sourceTree = BUILT_PRODUCTS_DIR;')
sources_group = add('sources', f'isa = PBXGroup; children = {array([v[0] for v in app_files])}; name = IRO; sourceTree = "<group>";')
test_group = add('tests', f'isa = PBXGroup; children = {array([v[0] for v in tests])}; name = Tests; sourceTree = "<group>";')
resources_group = add('resources', f'isa = PBXGroup; children = {array([v[0] for v in assets])}; name = Resources; sourceTree = "<group>";')
products_group = add('products', f'isa = PBXGroup; children = {array([app_product, test_product])}; name = Products; sourceTree = "<group>";')
main_group = add('main', f'isa = PBXGroup; children = {array([sources_group, resources_group, test_group, products_group])}; sourceTree = "<group>";')


def phase(key, cls, files):
    return add(key, f'isa = {cls}; buildActionMask = 2147483647; files = {array(files)}; runOnlyForDeploymentPostprocessing = 0;')


app_phases = [phase('app-sources', 'PBXSourcesBuildPhase', [v[1] for v in app_files]), phase('app-frameworks', 'PBXFrameworksBuildPhase', []), phase('app-resources', 'PBXResourcesBuildPhase', [v[1] for v in assets])]
test_phases = [phase('test-sources', 'PBXSourcesBuildPhase', [v[1] for v in tests]), phase('test-frameworks', 'PBXFrameworksBuildPhase', []), phase('test-resources', 'PBXResourcesBuildPhase', [])]


def configuration_list(key, common):
    configurations = []
    for name in ['Debug', 'Release']:
        settings = dict(common)
        if key in ('app', 'test'):
            previous = existing_objects.get(ident(key + name), {}).get('buildSettings', {})
            for signing_key in ('DEVELOPMENT_TEAM', 'PRODUCT_BUNDLE_IDENTIFIER', 'CODE_SIGN_STYLE',
                                'CODE_SIGN_IDENTITY', 'PROVISIONING_PROFILE_SPECIFIER'):
                if signing_key in previous:
                    settings[signing_key] = previous[signing_key]
        if key == 'project':
            settings.update({'SWIFT_OPTIMIZATION_LEVEL': '-Onone' if name == 'Debug' else '-O', 'DEBUG_INFORMATION_FORMAT': 'dwarf' if name == 'Debug' else 'dwarf-with-dsym', 'ENABLE_TESTABILITY': 'YES' if name == 'Debug' else 'NO', 'SWIFT_ACTIVE_COMPILATION_CONDITIONS': 'DEBUG' if name == 'Debug' else '', 'ONLY_ACTIVE_ARCH': 'YES' if name == 'Debug' else 'NO'})
        if key == 'project':
            previous = existing_objects.get(ident(key + name), {}).get('buildSettings', {})
            if 'SWIFT_OPTIMIZATION_LEVEL' in previous:
                settings['SWIFT_OPTIMIZATION_LEVEL'] = previous['SWIFT_OPTIMIZATION_LEVEL']
        body = ' '.join(f'{k} = {quote(v)};' for k, v in settings.items())
        configurations.append(add(key + name, f'isa = XCBuildConfiguration; buildSettings = {{{body}}}; name = {name};'))
    return add(key + 'config', f'isa = XCConfigurationList; buildConfigurations = {array(configurations)}; defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;')


project_config = configuration_list('project', {'CLANG_ENABLE_MODULES': 'YES', 'CLANG_ENABLE_OBJC_ARC': 'YES', 'IPHONEOS_DEPLOYMENT_TARGET': '17.0', 'SDKROOT': 'iphoneos', 'SWIFT_VERSION': '5.0', 'SWIFT_STRICT_CONCURRENCY': 'targeted', 'ENABLE_USER_SCRIPT_SANDBOXING': 'YES'})
app_config = configuration_list('app', {'PRODUCT_NAME': '$(TARGET_NAME)', 'PRODUCT_BUNDLE_IDENTIFIER': 'vn.iro.editor.ios', 'INFOPLIST_FILE': os.path.relpath(SOURCE / 'Resources/Info.plist', ROOT), 'GENERATE_INFOPLIST_FILE': 'NO', 'CODE_SIGN_STYLE': 'Automatic', 'TARGETED_DEVICE_FAMILY': '1,2,6', 'SUPPORTS_MACCATALYST': 'YES', 'DERIVE_MACCATALYST_PRODUCT_BUNDLE_IDENTIFIER': 'NO', 'SUPPORTS_MAC_DESIGNED_FOR_IPHONE_IPAD': 'YES', 'MACOSX_DEPLOYMENT_TARGET': '14.0', 'ASSETCATALOG_COMPILER_APPICON_NAME': 'AppIcon', 'LD_RUNPATH_SEARCH_PATHS': '$(inherited) @executable_path/Frameworks', 'SWIFT_EMIT_LOC_STRINGS': 'YES'})
test_config = configuration_list('test', {'PRODUCT_NAME': '$(TARGET_NAME)', 'PRODUCT_BUNDLE_IDENTIFIER': 'vn.iro.editor.ios.tests', 'GENERATE_INFOPLIST_FILE': 'YES', 'CODE_SIGN_STYLE': 'Automatic', 'TARGETED_DEVICE_FAMILY': '1,2,6', 'SUPPORTS_MACCATALYST': 'YES', 'MACOSX_DEPLOYMENT_TARGET': '14.0', 'TEST_HOST': '$(BUILT_PRODUCTS_DIR)/IRO.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/IRO', 'BUNDLE_LOADER': '$(TEST_HOST)', 'LD_RUNPATH_SEARCH_PATHS': '$(inherited) @executable_path/Frameworks @loader_path/Frameworks'})
app_target = add('app-target', f'isa = PBXNativeTarget; buildConfigurationList = {app_config}; buildPhases = {array(app_phases)}; buildRules = (); dependencies = (); name = IRO; productName = IRO; productReference = {app_product}; productType = "com.apple.product-type.application";')
proxy = add('proxy', f'isa = PBXContainerItemProxy; containerPortal = {ident("project-root")}; proxyType = 1; remoteGlobalIDString = {app_target}; remoteInfo = IRO;')
dependency = add('dependency', f'isa = PBXTargetDependency; target = {app_target}; targetProxy = {proxy};')
test_target = add('test-target', f'isa = PBXNativeTarget; buildConfigurationList = {test_config}; buildPhases = {array(test_phases)}; buildRules = (); dependencies = ({dependency},); name = IROTests; productName = IROTests; productReference = {test_product}; productType = "com.apple.product-type.bundle.unit-test";')
project = add('project-root', f'isa = PBXProject; attributes = {{BuildIndependentTargetsInParallel = YES; LastUpgradeCheck = 1600; TargetAttributes = {{{app_target} = {{CreatedOnToolsVersion = 16.0;}}; {test_target} = {{CreatedOnToolsVersion = 16.0; TestTargetID = {app_target};}};}};}}; buildConfigurationList = {project_config}; compatibilityVersion = "Xcode 14.0"; developmentRegion = vi; hasScannedForEncodings = 0; knownRegions = (vi, en, Base); mainGroup = {main_group}; productRefGroup = {products_group}; projectDirPath = ""; projectRoot = ""; targets = ({app_target}, {test_target},);')
folder = ROOT / 'IRO.xcodeproj'
folder.mkdir(exist_ok=True)
contents = '// !$*UTF8*$!\n{\narchiveVersion = 1;\nclasses = {};\nobjectVersion = 56;\nobjects = {\n'
contents += ''.join(f'{key} = {{{body}}};\n' for key, body in objects.items())
contents += f'}};\nrootObject = {project};\n}}\n'
(folder / 'project.pbxproj').write_text(contents)
scheme = folder / 'xcshareddata/xcschemes/IRO.xcscheme'
scheme.parent.mkdir(parents=True, exist_ok=True)
app_ref = f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{app_target}" BuildableName="IRO.app" BlueprintName="IRO" ReferencedContainer="container:IRO.xcodeproj"/>'
test_ref = f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{test_target}" BuildableName="IROTests.xctest" BlueprintName="IROTests" ReferencedContainer="container:IRO.xcodeproj"/>'
scheme.write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
  <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>
    <BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{app_ref}</BuildActionEntry>
    <BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="NO">{test_ref}</BuildActionEntry>
  </BuildActionEntries></BuildAction>
  <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES" codeCoverageEnabled="YES"><Testables><TestableReference skipped="NO">{test_ref}</TestableReference></Testables></TestAction>
  <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{app_ref}</BuildableProductRunnable></LaunchAction>
  <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{app_ref}</BuildableProductRunnable></ProfileAction>
  <AnalyzeAction buildConfiguration="Debug"/>
  <ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
''')
print(f'Generated IRO.xcodeproj: {len(app_sources)} app Swift files, {len(test_sources)} test files, {len(resources)} resources.')
