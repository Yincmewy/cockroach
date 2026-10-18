# Cockroach model attribution

- Title: Cockroach
- Original author: CK (https://sketchfab.com/xcellf)
- Original source: https://sketchfab.com/3d-models/cockroach-cead55b8aa8643d48d67240bad028592
- License: Creative Commons Attribution 4.0 International (CC BY 4.0)
- License text: https://creativecommons.org/licenses/by/4.0/
- Supplied archive: 蟑螂_2.zip
- Source entry: 83926383/glbfile.glb
- Mod asset: assets/cockroach/models/entity/cockroach.glb

Attribution above is copied from the supplied GLB's asset.extras metadata.
The GLB is included byte-for-byte, unmodified. Its embedded base-color PNG is
also extracted unchanged as the entity renderer's texture-location fallback.
Scaling and centering happen only at render time. The original animation is
retained in the file. Since 1.4.0 this mod samples its original joint tracks and
implements weighted skinning separately, as Model Loader0 1.0.5 does not provide it.
This mod does not claim authorship of the model, imply endorsement by
CK, or apply its code license to these CC BY 4.0 assets.

The `cockroach_medium.glb` (3983 triangles) and `cockroach_low.glb` (794 triangles)
assets are modified static LOD derivatives made with meshoptimizer 1.2.0: geometry
is simplified, skin/animation channels are removed, and the original base-color
image is embedded unchanged. They retain CK attribution and CC BY 4.0 licensing.

Model Loader0 1.0.5 by Luoee (mod ID: modelloader, MIT License) is bundled as an
unmodified nested dependency using Forge Jar-in-Jar. See THIRD_PARTY_NOTICES.md.

## Flight rig added in 1.3.0

`cockroach_flight_high.glb`, `cockroach_flight_medium.glb`, and
`cockroach_flight_low.glb` are modified derivatives of CK's model under CC BY 4.0.
The original left/right elytra and six legs have been separated into independently
hinged rigid meshes. Two curved hindwing membranes and a procedural amber vein
texture were newly modeled for this mod. High/medium/low tiers have 29,474 / 3,999 /
812 triangles and retain the original body UVs and embedded base-color image.
The source has no wing bones or flight animation; its `Take 001` clip affects
the head, abdomen and legs. Flight uses new runtime rigid-part transforms for
elytra opening, membrane flapping and leg tucking, without weighted skinning.
Regenerate these assets with `node tools/generate-flight-models.mjs`.

## Original walking animation adapted in 1.4.0

`cockroach_walk.bin` is a derived cache of CK's original `Take 001` animation:
43 authored transform channels, 31 joints, original inverse bind matrices and
per-vertex joint weights. The source tracks are sampled into 48 shared bone poses;
the last keyed frame is interpolated back to the first over one 24-fps frame.
Only vertical root placement is adjusted to keep the feet on the game's ground
plane. All three ground LODs retain the original surviving vertices' skin weights.
The mod uses GPU weighted skinning, with a matching CPU path for compatibility.
The original gait is not replaced by procedural leg swings. Regenerate the cache
with `node tools/generate-walk-animation.mjs`. This derivative retains CK's
attribution and CC BY 4.0 licensing.
