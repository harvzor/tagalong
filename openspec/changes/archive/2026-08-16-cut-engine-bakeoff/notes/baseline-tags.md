# Baseline tags — xiaomi-poco-x5.mp4

Recorded via `ffprobe -v quiet -print_format json -show_format -show_streams xiaomi-poco-x5.mp4` (desktop ffmpeg/ffprobe 8.1.1), 2026-08-16.

## Format (container) tags

| Key | Value |
|---|---|
| `major_brand` | `mp42` |
| `minor_version` | `0` |
| `compatible_brands` | `isommp42` |
| `creation_time` | `2026-08-09T21:39:47.000000Z` |
| `location` | `+52.5182+013.4064/` |
| `location-eng` | `+52.5182+013.4064/` |
| `com.android.version` | `14` |
| `com.android.manufacturer` | `Xiaomi` |
| `com.android.model` | `22101320G` |
| `com.xiaomi.product.marketname` | `POCO X5 Pro 5G` |

## Video stream (index 0)

- codec: `h264` (High profile, level 40), `avc1`, 1920x1080, `yuv420p`
- `r_frame_rate`: 30/1, `nb_frames`: 146, duration ~4.878s
- bit_rate: 16,909,787
- **Display Matrix side data: `rotation: -90`**
- stream tags: `creation_time=2026-08-09T21:39:47.000000Z`, `language=eng`, `handler_name=VideoHandle`

## Audio stream (index 1)

- codec: `aac` (LC), `mp4a`, 48000 Hz, stereo
- `nb_frames`: 225, duration ~4.800s, bit_rate: 96,010
- stream tags: `creation_time=2026-08-09T21:39:47.000000Z`, `language=eng`, `handler_name=SoundHandle`

## Container

- `format_name`: `mov,mp4,m4a,3gp,3g2,mj2`
- duration: 4.890922s, size: 11,663,072 bytes, bit_rate: 19,077,093

## Assertion set derived from this baseline

`source_tags` (format-level, for the `⊆` check) = the 10 keys in the Format table above.
Plus per-stream: `creation_time`, `rotation=-90` (video), codec identity (`h264`/`aac`, same `codec_tag_string`).

Capture date for `MediaStore.DATE_TAKEN` comparison: `creation_time` → 2026-08-09T21:39:47Z.
