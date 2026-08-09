"""
Perceptual hashing on the Python side. Used mainly for video keyframe pHash
computation (which the Java FileHashingService doesn't cover, since it
operates on single uploaded images). Uses the same conceptual algorithm
family (DCT-based perceptual hash) as JImageHash's PerceptiveHash on the
Java side so hashes computed by either service are comparable.
"""

from PIL import Image
import io
import imagehash


def compute_perceptual_hash(image_bytes: bytes) -> str:
    image = Image.open(io.BytesIO(image_bytes))
    # phash = DCT-based perceptual hash, matching JImageHash's PerceptiveHash approach.
    hash_value = imagehash.phash(image, hash_size=8)  # 64-bit, matches Java-side PerceptiveHash(64)
    return str(hash_value)


def hamming_distance(hash_a: str, hash_b: str) -> int:
    return imagehash.hex_to_hash(hash_a) - imagehash.hex_to_hash(hash_b)
