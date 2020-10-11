# CloudBurst
A simple CLI tool to download music from [SoundCloud](https://soundcloud.com).

### Features
- Download track by URL
- Download all liked tracks for a user
- Tagged with ID3 metadata (including album art)  

### Requirements
- Requires Java 11+ to run

### Example Usage
```
# download a specific track
java -jar cloudburst.jar -t "https://soundcloud.com/plexitofer/cupid-groove"

# download all liked tracks for user
java -jar cloudburst.jar -u 123456 --likes
```

### Options
```
  -d, --downloads  <arg>   Download folder location
  -l, --likes              Download all liked songs. Requires 'user' arg to be
                           set at least once previously
  -t, --track  <arg>       Download single track via URL
  -u, --user  <arg>        Soundcloud Account ID. To find this ID, go to your
                           cookies and look at your 'oauth_token'. Example:
                           oauth_token='1-123456-7777777-ABCdefGHIjkLMn', then
                           your ID would be '7777777'. NOTE: This is not the ID
                           you see in your profile URL.
  -h, --help               Show help message
```




