const aws = require('aws-sdk');
const crypto = require('crypto');
const fs = require('fs');
const s3 = new aws.S3();

class Loader {
    constructor(prefix, bucket) {
        this.bucket = bucket;
        this.prefix = prefix === undefined ||
                prefix === null ||
                prefix === '' ? '' : prefix;

    }

    download(digest) {
        // ugly workaround for NXP-25533, DO NOT merge
        if(digest.indexOf('default:') >= 0){
            digest = digest.slice(8);
        }
        let key = this.prefix + digest;
        let bucket = this.bucket;
        console.log('Downloading', key, "from:", bucket);
        return new Promise(function(resolve, reject) {
            let info = {
                Bucket: bucket,
                Key: key
            };
            resolve(s3.getObject(info));
        });
    }

    upload(info, original) {
        const file = fs.readFileSync(info.path);
        const digest = Loader.getHash(file);
        let key = this.prefix + digest;
        console.log('Uploading', key);
        const params = {
            Bucket: this.bucket,
            Key: key,
            Body: file,
            ContentType: 'image/jpg',
            Metadata: {
                originalFileDigest: original
            }
        };

        return new Promise(function(resolve, reject) {
            s3.putObject(params, function(err, data) {
                if (err) {
                    console.log('Error on upload', err);
                    reject(err);
                    return;
                }
                resolve({
                    data: data,
                    length: file.length,
                    digest: digest,
                    name: info.size.name,
                    width: info.size.width,
                    height: info.size.height
                });
            });
        });
    }

    static getHash(file) {
        return crypto.createHash('md5')
            .update(file)
            .digest('hex');
    }
}

module.exports = Loader;