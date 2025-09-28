import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import axios from 'axios';

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
    engine: { 
      baseUrl: string; 
      fileName: string; 
      latestEndpoint: string;
    };
    jdk: { baseUrl: string };
  };
}

interface GitHubRelease {
  tag_name: string;
  assets: Array<{
    name: string;
    browser_download_url: string;
  }>;
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

  async checkAndDownloadLatestCLI(): Promise<boolean> {
    try {
      const latestVersion = await this.getLatestCLIVersion();
      const currentVersion = this.getCurrentCliVersion();
      
      if (this.isNewerVersion(latestVersion, currentVersion)) {
        console.log(`New CLI version available: ${latestVersion} (current: ${currentVersion})`);
        await this.downloadAndInstallCLI(latestVersion);
        return true;
      }
      
      return false;
    } catch (error) {
      console.warn(`Failed to check for CLI updates: ${error instanceof Error ? error.message : String(error)}`);
      return false;
    }
  }

  private async getLatestCLIVersion(): Promise<string> {
    const response = await axios.get<GitHubRelease>(
      'https://api.github.com/repos/EPI-Studios/Moud/releases/latest',
      {
        timeout: 10000,
        headers: { 'User-Agent': 'Moud-CLI' }
      }
    );
    return response.data.tag_name.replace(/^v/, '');
  }

  private isNewerVersion(latest: string, current: string): boolean {
    const latestParts = latest.split('.').map(Number);
    const currentParts = current.split('.').map(Number);
    
    for (let i = 0; i < Math.max(latestParts.length, currentParts.length); i++) {
      const latestPart = latestParts[i] || 0;
      const currentPart = currentParts[i] || 0;
      
      if (latestPart > currentPart) return true;
      if (latestPart < currentPart) return false;
    }
    
    return false;
  }

  private async downloadAndInstallCLI(version: string): Promise<void> {
    const response = await axios.get<GitHubRelease>(
      'https://api.github.com/repos/EPI-Studios/Moud/releases/latest',
      {
        timeout: 10000,
        headers: { 'User-Agent': 'Moud-CLI' }
      }
    );

    const cliAsset = response.data.assets.find(asset => 
      asset.name.includes('moud-cli') && asset.name.endsWith('.tgz')
    );

    if (!cliAsset) {
      throw new Error('CLI package not found in latest release');
    }

    const tempDir = fs.mkdtempSync(path.join(require('os').tmpdir(), 'moud-cli-'));
    const downloadPath = path.join(tempDir, cliAsset.name);

    try {
      console.log('Downloading latest CLI...');
      await this.downloadFile(cliAsset.browser_download_url, downloadPath);
      
      console.log('Installing CLI update...');
      await this.installCLI(downloadPath);
      
      console.log(`Successfully updated to CLI version ${version}`);
    } finally {
      fs.rmSync(tempDir, { recursive: true, force: true });
    }
  }

  private async downloadFile(url: string, destination: string): Promise<void> {
    const response = await axios({
      url,
      method: 'GET',
      responseType: 'stream',
      headers: { 'User-Agent': 'Moud-CLI' }
    });

    const writer = fs.createWriteStream(destination);
    response.data.pipe(writer);

    return new Promise((resolve, reject) => {
      writer.on('finish', resolve);
      writer.on('error', reject);
    });
  }

  private async installCLI(packagePath: string): Promise<void> {
    const { spawn } = await import('child_process');
    
    return new Promise((resolve, reject) => {
      const npmProcess = spawn('npm', ['install', '-g', packagePath], {
        stdio: 'inherit'
      });

      npmProcess.on('close', (code) => {
        if (code === 0) {
          resolve();
        } else {
          reject(new Error(`npm install failed with code ${code}`));
        }
      });

      npmProcess.on('error', reject);
    });
  }

  getCurrentCliVersion(): string {
    return this.packageJson.version;
  }

  async getCompatibleEngineVersion(): Promise<string> {
    const cliVersion = this.getCurrentCliVersion();
    const compatibility = this.config.compatibility[cliVersion];
    if (!compatibility) {
      throw new Error(`No compatibility mapping found for CLI version ${cliVersion}`);
    }
    
    if (compatibility.engine === 'latest') {
      return await this.getLatestEngineVersion();
    }
    
    return compatibility.engine;
  }

  async getLatestEngineVersion(): Promise<string> {
    try {
      const response = await axios.get<GitHubRelease>(
        'https://api.github.com/repos/EPI-Studios/Moud/releases/latest',
        {
          timeout: 10000,
          headers: { 'User-Agent': 'Moud-CLI' }
        }
      );
      return response.data.tag_name.replace(/^v/, '');
    } catch (error) {
      throw new Error(`Failed to fetch latest engine version: ${error instanceof Error ? error.message : String(error)}`);
    }
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

  async getEngineDownloadUrl(): Promise<string> {
    const version = await this.getCompatibleEngineVersion();
    const { baseUrl, fileName } = this.config.downloads.engine;
    
    if (version === 'latest') {
      const actualVersion = await this.getLatestEngineVersion();
      return `${baseUrl}/${actualVersion}/${fileName}`;
    }
    
    return `${baseUrl}/${version}/${fileName}`;
  }

  getJDKDownloadUrl(os: string, arch: string): string {
    const jdkVersion = this.getJDKVersion();
    const { baseUrl } = this.config.downloads.jdk;
    return `${baseUrl}/${os}/${arch}/jdk/hotspot/normal/eclipse`;
  }

    validateCompatibility(): boolean {

      return true;
    }

}