for f in *.webm; do
  name="${f%.webm}"

  # 1. 生成调色板
  ffmpeg -y -i "$f" \
    -vf "fps=10,scale=240:-1:flags=lanczos,palettegen" \
    "${name}_palette.png"

  # 2. 生成 GIF
  ffmpeg -y -i "$f" -i "${name}_palette.png" \
    -filter_complex "fps=10,scale=240:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=4" \
    "${name}.gif"

  # 3. 可选：删掉调色板
  rm "${name}_palette.png"
done