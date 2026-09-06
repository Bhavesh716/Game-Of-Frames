# mobilefacenet.tflite — provenance and license

**Architecture / training:** MobileFaceNet, trained by the
[`sirius-ai/MobileFaceNet_TF`](https://github.com/sirius-ai/MobileFaceNet_TF) project
(TensorFlow implementation of *MobileFaceNets: Efficient CNNs for Accurate Real-Time Face
Verification on Mobile Devices*, Chen et al., https://arxiv.org/abs/1804.07573). Reported
accuracy: 99.4%+ on LFW. License: **Apache License 2.0**.

**This file** is the widely-mirrored TFLite conversion of that model, obtained from
[`MCarlomagno/FaceRecognitionAuth`](https://github.com/MCarlomagno/FaceRecognitionAuth)
(`assets/mobilefacenet.tflite` on the `develop` branch), a project licensed
**BSD 3-Clause**. No modifications were made to the model weights or graph.

**Verified tensor shapes** (inspected directly with the LiteRT Python interpreter before
integration — not assumed):

- Input: `input`, shape `[1, 112, 112, 3]`, `float32`, NHWC, RGB channel order.
- Output: `embeddings`, shape `[1, 192]`, `float32` (192-dimensional face embedding).
- No quantization (plain float32 model, ~5.2 MB on disk).

**Required preprocessing** (matches the reference Flutter/tflite_flutter consumer of this
same file, cross-checked empirically — see `FaceEmbedderCalibrationNotes` referenced from
the app README): convert the face crop to RGB, resize to 112×112, then normalize each
channel as `(pixel - 128.0) / 128.0` (maps `[0, 255]` to approximately `[-1, 1)`). The
raw 192-d output is L2-normalized by the app before use, so cosine similarity between two
embeddings is a plain dot product.

See the project root `README.md` section "Face Embedding Model" for how the identity
similarity threshold was calibrated, and for full pipeline documentation.
