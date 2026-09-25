# Approaches to retaining metadata

There are a lot of ways to try to retain metadata.

## Goals

### Retain `gps`

It seems that Android phones typically record gps information to `/moov/udta/©xyz` - this needs to be preserved.

### Retain `/moov/meta`

Xiaomi phones write to `/moov/meta/keys/` with a key of `com.xiaomi.product.marketname` with a value in `/moov/meta/ilst` of `POCO X5 Pro 5G`:

```
/moov/meta
    ├─ keys
    │     ├─ ID 1   mdta  "com.android.version"
    │     ├─ ID 2   mdta  "com.android.manufacturer"
    │     ├─ ID 3   mdta  "com.android.model"
    │     ├─ ID 4   mdta  "com.xiaomi.product.marketname"
    │     └─ ID 5   mdta  "com.video.file.type"
    │
    └─ ilst
          ├─ ID 1   data(utf8)  "14"
          ├─ ID 2   data(utf8)  "Xiaomi"
          ├─ ID 3   data(utf8)  "22101320G"
          ├─ ID 4   data(utf8)  "POCO X5 Pro 5G"
          └─ ID 5   data(bin)   <4 raw bytes, undecoded>
```

## Solutions

### Get FFmpeg to handle meta

#### `-map_metadata 0:g`

FFmpeg by default uses the `-map_metadata 0:g` flag which means "copy all global metadata from the first input file".

This option however has a caveat in that it normalises the location intentionally from `/moov/udta/©xyz` to `/moov/udta/loci`, a similar issue is mentioned here: https://trac.ffmpeg.org/ticket/4209

What's wrong with the normalised location?

- Google Photos does not read the normalised location so does not show the location of the video on the map correctly
- At least when not provided, the altitude is set to 0 which adds extra fake information
- The x/y coordinates can change because the xyz value is stored as a decimal string and the loci value is a 32-bit fixed point

### `-movflags +use_metadata_tags`

Without this flag:

- the  `/moov/meta/keys/` entries such as `com.android.*` and `com.xiaomi.product.marketname` are dropped
- the `/moov/mvhd/creation_time` and other record dates are dropped from the metadata

### Use ExifTool

ExifTool allows us to copy metadata from one file to another, but some tags readable but not writable.

ExifTool cannot write to `com.xiaomi.product.marketname`, so having a 1:1 copy via ExifTool is not possible. 

### Manually try to map all keys

Tagalong could try to maintain a list of metadata that video files have and try to copy them the output video. This would grant Tagalong the most flexibility but also require a lot of work to make sure that meta isn't lost between all the different types of devices that can record and make up their own metadata.
