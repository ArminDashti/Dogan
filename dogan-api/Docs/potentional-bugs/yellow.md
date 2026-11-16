# Yellow: Frame image path inside container

`GET /frame/image` serves files from paths stored in SQLite. Paths are written relative to the frames directory inside the container (`/data/frames`). If the server process cwd differs from the frames root, image download could fail â€” verify path resolution if moving to absolute paths.
