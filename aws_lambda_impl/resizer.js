const gm = require('gm')
    .subClass({
        imageMagick: true
    });
const crypto = require("crypto");

class Resizer {

    constructor() {
        this.original = {
          path: Resizer.getOriginalPath(),
          size: {
              name: 'OriginalJpeg',
              width: 0,
              height: 0
          }
        };
    }

    static getOriginalPath() {
        return '/tmp/0.jpg';
    }

    getSize(path) {
        return new Promise(function(resolve, reject) {
            gm(path).size(function(err, size) {
                if (err) reject(err);

                const width = size.width;
                const height = size.height;
                console.log('Width_X_Height:', width, height);
                console.log('Path', path);
                resolve({
                    path: path,
                    size: {
                        width: size.width,
                        height: size.height
                    }
                });
            });
        });
    }

    transform(path, size) {
        return new Promise(function(resolve, reject) {
            console.log('Reading image from', path);
            let image = gm(path + '[0]');
            image = Resizer._transform(image, size);
            image = image.flatten();

            const out = '/tmp/resized_' + size.width + '.jpg';
            console.log('Writing temporary file to', out);
            image.write(out, function (err) {
                if (err) reject(err);
                else resolve({
                    path: out,
                    size: size
                });
            });
        });
    }

    transformFromS3Object(s3Obj, size);

    writeStream(obj, size) {
        return new Promise(function (resolve, reject) {
            const path = '/tmp/' + new Date().getMilliseconds().toString() + '.jpg';
            let image = gm(obj.createReadStream(), path + '[0]');
            image = Resizer._transform(image, size);

            if (size === null) {
                size = {
                    width: 0,
                    height: 0
                };
            }
            let out = Resizer.getOriginalPath();
            image.write(out, function (err) {
                if (err) reject(err);
                else resolve({
                    path: out,
                    size: size
                });
            });
        });
    }

    static _transform(image, size=null) {
        if (size !== null) {
            image = image.resize(size.width, size.height);
        }

        return image.flatten();
    }
}

module.exports = Resizer;