'use strict';

const gm = require('gm')
    .subClass({
        imageMagick: true
    });
const aws = require('aws-sdk');
const crypto = require('crypto');
const fs = require('fs');
const s3 = new aws.S3();
const request = require('request');

function sort(dict) {
    const items = Object.keys(dict).map(function (key) {
        return [key, dict[key]];
    });

    // Sort the array based on the second element
    items.sort(function(first, second) {
        return second[1] - first[1];
    });

    return items;
}

function getSize(path) {
    return new Promise(function(resolve, reject) {
        gm(path).size(function(err, size) {
            if (err) reject(err);
            const width = size.width;
            const height = size.height;
            console.log('Width and Height are', width, height);
            resolve({
                path: path,
                width: width,
                height: height
            });
        });
    });
}

function calcSize(maxSizeInfo, size) {
    var width = size.width;
    var height = size.height;
    var ratio = 0;
    const maxSize = maxSizeInfo[1];
    if (width > maxSize) {
        ratio = maxSize / width;
        height = height * ratio;
        width = width * ratio;
    }

    if (height > maxSize) {
        ratio = maxSize / height;
        height = height * ratio;
        width = width * ratio;
    }

    return {
        name: maxSizeInfo[0],
        width: width,
        height: height
    };
}

function download(bucket, filename, path) {
    return new Promise(function(resolve, reject) {
        s3.getObject({
            Bucket: bucket,
            Key: filename
        }, function(err, res) {
            if (err) {
                console.log('Error on download', err);
                reject(err);
                return;
            }
            console.log('Writing original file to', path);
            fs.writeFileSync(path, res.Body);
            resolve(path);
        });
    });
}

function transform(path, size) {
    return new Promise(function(resolve, reject) {
        console.log('Reading original file from',  path);
        const out = '/tmp/resized_' + size.width + '.jpg';
        console.log('Writing temporary file to', out);

        gm(path + '[0]')
            .resize(size.width, size.height)
            .flatten()
            .write(out, function (err) {
                if (err) reject(err);
                else resolve({
                    path: out,
                    size: size
                });
            });
    });
}

function upload(bucket, info, original) {
    const file = fs.readFileSync(info.path);
    const digest = crypto.createHash('md5').update(file).digest('hex');
    console.log('uploading', digest);
    const params = {
        Bucket: bucket,
        Key: digest,
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

function buildPath(host, cb, success) {
    const domain = host + '/site/lambda/';
    if (success) {
        return domain + 'success/' + cb;
    } else {
        return domain + 'error/' + cb;
    }
}

function callREST(meta, host, cb, startTime, success=true, error=null) {
    var json = {};
    json.time = new Date() - startTime;
    json.images = meta;
    json.error = error;

    console.log('JSON: ', json);
    const path = buildPath(host, cb, success);
    console.log("request: ", path);
    return new Promise(function (resolve, reject) {
        request.post(path, {
            json: json,
            headers: {
                'Content-Type': 'application/json'
            }
        }, function (err, response, _) {
            if (err) {
                console.log('Error on callREST', err);
                return;
            }
            if (success) {
                resolve(response);
            } else {
                resolve(error);
            }
        });
    });
}

exports.handler = (event, context, callback) => {
    const start = new Date();
    console.log(event);
    const host = event.host;
    const cb = event.cbId;
    const bucket = event.input.bucket;
    const digest = event.input.digest;
    const filename = event.input.filename;

    const path = '/tmp/' + filename;

    var sizes = JSON.parse(event.input.sizes);
    sizes = sort(sizes);

    download(bucket, digest, path)
        .then(function(info) {
            return getSize(info)
        })
        .then(function(info) {

            var ps = [];
            for (const size of sizes) {
                console.log('Size processing', size);
                const theSize = calcSize(size, info);
                ps.push(transform(info.path, theSize));
            }
            return Promise.all(ps);
        })
        .then(function(infos) {
            var ps = [];
            for (const info of infos) {
                console.log('About to upload', info);
                ps.push(upload(bucket, info, digest));
            }
            return Promise.all(ps);
        })
        .catch(function(err) {
            console.log('Error occurred', err);
            callREST(null, host, cb, start, false, err)
                .then(function () {
                    callback(err);
                })
                .catch(function(err) {
                    console.log(err);
                });
        })
        .then(function (result) {
            if (result)
                return callREST(result, host, cb, start);
        })
        .catch(function (err) {
            console.log('Couldn\'t complete the chan', err);
            callback(err);
        });
};