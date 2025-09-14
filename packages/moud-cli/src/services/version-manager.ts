import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

interface VersionConfig {
  versions: {
    cli: string;
    sdk: string;
    engine: string;
    jdk: string;
  };
  compatibility: Record<string, { sdk: string; engine: string }>;
  downloads: {
    engine: { baseUrl: string; fileName: string };
    jdk: { baseUrl: string };
  };
}

export class VersionManager {
  private config: VersionConfig;
  private packageJson: { version: string };

  constructor() {
    const configPath = path.join(__dirname, '..', 'config.json');
    const packagePath = path.join(__dirname, '..', '..', 'package.json');

    this.config = JSON.parse(fs.readFileSync(configPath, 'utf-8'));
    this.packageJson = JSON.parse(fs.readFileSync(packagePath, 'utf-8'));
  }

  getCurrentCliVersion(): string {
    return this.packageJson.version;
  }

  getCompatibleEngineVersion(): string {
    const cliVersion = this.getCurrentCliVersion();
    const compatibility = this.config.compatibility[cliVersion];
    if (!compatibility) {
      throw new Error(`No compatibility mapping found for CLI version ${cliVersion}`);
    }
    return compatibility.engine;
  }

  getCompatibleSDKVersion(): string {
    const cliVersion = this.getCurrentCliVersion();
    const compatibility = this.config.compatibility[cliVersion];
    if (!compatibility) {
      throw new Error(`No compatibility mapping found for CLI version ${cliVersion}`);
    }
    return compatibility.sdk;
  }

  getJDKVersion(): string {
    return this.config.versions.jdk;
  }

  getEngineDownloadUrl(): string {
    const version = this.getCompatibleEngineVersion();
    const { baseUrl, fileName } = this.config.downloads.engine;
    return `${baseUrl}/v${version}/${fileName}`;
  }

  getJDKDownloadUrl(os: string, arch: string): string {
    const jdkVersion = this.getJDKVersion();
    const { baseUrl } = this.config.downloads.jdk;
    return `${baseUrl}/${os}/${arch}/jdk/hotspot/normal/eclipse`;
  }

  validateCompatibility(): boolean {
    const cliVersion = this.getCurrentCliVersion();
    return !!this.config.compatibility[cliVersion];
  }
}