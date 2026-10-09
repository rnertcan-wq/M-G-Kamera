# Third-party components

## KAIR DnCNN color blind

- Author: Kai Zhang / KAIR; MIT license bundled in `kair/LICENSE.txt`.
- Training/architecture reference: https://github.com/cszn/KAIR ; DnCNN paper: https://arxiv.org/abs/1608.03981
- Author's released weights: https://github.com/cszn/KAIR/releases/download/v1.0/dncnn_color_blind.pth
- Downloaded weight SHA-256: `dfe11a6b703304df82cdf236f431fc6492766c7b9119d97e943edeb392c710a3`
- Native tensor input/output: float32 RGB NCHW, range approximately 0..1, dynamic spatial dimensions, same resolution output; residual subtraction x - model(x).
- Architecture: 20 convolution layers, 64 hidden channels, 3×3 kernel, ReLU between layers. Released state_dict has 40 parameter tensors; loaded strictly with `torch.load(weights_only=True)`. No arbitrary pickle execution is used.
- Exported using PyTorch CPU 2.5.1 and ONNX 1.17.0, opset 17; verified with ONNX checker, PyTorch reference and ONNX Runtime CPU 1.20.1. Mobile runtime is 1.20.0.
- ONNX SHA-256: `11fed3af8c0de2e7e6ac90d946220191c75707467f6a05849be2f01429414aaf`. App verifies this before creating a session.
- Export/inference evidence: `kair/model-validation.json`. Synthetic tests establish conversion and tiling correctness; they do not establish superiority on CK7n photographs or against another phone.
- Tile size 256 with halo 20 (receptive field 41), using immutable original JPEG region decoding. No in-place feedback between tiles. Output is 65% neural prediction + 35% original. No upscaling or new pixels from resizing.
- Full-resolution inference can take minutes on a phone. Two CPU threads; progress/cancel checks; original JPEG always retained. No photo upload or inference API.

## Microsoft ONNX Runtime Android 1.20.0

- Official Maven artifact: https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.20.0/onnxruntime-android-1.20.0.aar
- AAR SHA-256: `07a8f71ef890afed8c6087a56220e6d558a492804276ee2dd7cb7f6262242027`; Maven SHA-1 sidecar verified at download time, with TLS verification enabled.
- Included: classes.jar and arm64-v8a libonnxruntime/libonnxruntime4j_jni. Target phone is arm64; other native architectures are not included.
- MIT license and ThirdPartyNotices bundled under `onnxruntime/`.
- Official project: https://github.com/microsoft/onnxruntime

No OEM camera code, binary libraries, face/beauty models or license bags from the user's APK are distributed.
