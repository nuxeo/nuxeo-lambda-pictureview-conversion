const request = require('request');

class Caller {

    constructor(host, cbId) {
        this.host = host;
        this.cbId = cbId;
    }

    call(meta, startTime, success=true, error=null) {
        var json = {};
        json.time = new Date() - startTime;
        json.images = meta;
        json.error = error;

        console.log('JSON:' + JSON.stringify(json));
        const path = this.buildPath(success);
        console.log("request:", path);
        return new Promise(function (resolve, reject) {
            request.post(path, {
                json: json,
                headers: {
                    'Content-Type': 'application/json'
                }
            }, function (err, response, _) {
                if (err) {
                    console.log('Error on callREST', err);
                    // reject(err);
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

    buildPath(success) {
        const domain = this.host + '/site/lambda/';
        if (success) {
            return domain + 'success/' + this.cbId;
        } else {
            return domain + 'error/' + this.cbId;
        }
    }

}

module.exports = Caller;
