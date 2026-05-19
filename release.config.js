module.exports = {
  branches: [
    { name: 'main' },
    { name: 'master' },
    { name: 'staging', prerelease: 'staging' },
  ],
  repositoryUrl: 'https://github.com/zoroplay/mts-v3-service.git',
  tagFormat: 'v${version}',
  plugins: [
    '@semantic-release/commit-analyzer',
    '@semantic-release/release-notes-generator',
    '@semantic-release/changelog',
    ['@semantic-release/github', {
      successComment: false,
      failComment: false,
      releasedLabels: false
    }],
    '@semantic-release/git',
  ],
  preset: 'angular',
};