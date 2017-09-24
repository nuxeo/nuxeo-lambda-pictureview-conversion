'use strict';

const Caller = require('api_caller');
const Loader = require('loader');
const Resizer = require('resizer');

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

function calcSize(maxSizeInfo, size) {
    let width = size.width;
    let height = size.height;
    let ratio = 0;
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

exports.handler = (event, context, callback) => {
    const start = new Date();
    console.log(event);
    const digest = event.input.digest;
    let sizes = JSON.parse(event.input.sizes);
    sizes = sort(sizes);

    let caller = new Caller(event.host, event.cbId);
    let loader = new Loader(event.prefix, event.bucket);
    let resizer = new Resizer();

    console.log('Starting call chain');
    loader.download(digest)
        .then(function(s3Obj) {
            return resizer.writeStream(s3Obj, null);
        })
        .then(function(info) {
            console.log("Getting size of", info);
            return resizer.getSize(info.path);
        })
        .then(function(info) {
            console.log('Got size:', info);
            let ps = [];
            console.log('Object processing', JSON.stringify(info));
            resizer.original.size.height = info.size.height;
            resizer.original.size.width = info.size.width;
            for (const size of sizes) {
                console.log('Size processing', size);
                ps.push(resizer.transform(info.path, calcSize(size, info)));
            }
            return Promise.all(ps);
        })
        .then(function(infos) {
            let ps = [];
            infos.push(resizer.original);
            for (const info of infos) {
                console.log('About to upload', info);
                ps.push(loader.upload(info, digest));
            }
            return Promise.all(ps);
        })
        .catch(function(err) {
            console.log('Error occurred', err);
            caller.call(null, start, false, err)
                .then(function () {
                    console.log("Called server on error", err.message);
                })
                .catch(function(err) {
                    console.log('Could not send data on error. Lambda error', err.message);
                });
        })
        .then(function (result) {
            if (result)
                return caller.call(result, start);
        })
        .catch(function (err) {
            console.log('Couldn\'t complete the chain', err);
        });
};