const process   = require('process')
const fs        = require('fs-extra')

const rollup    = require( 'rollup' )
const path      = require('path')
const camelcase = require('camelcase')

const resolve= require('rollup-plugin-node-resolve')
const babel  = require('rollup-plugin-babel')

console.log('=== building vue files ========================================')

var felibName = ''
if(process.argv.length >= 3) {
    felibName = process.argv[2]
} else {
    console.log('please provide a name for the felib to use')
    process.exit(-1)
}

console.log('building felib', felibName)

var basePath = './src/main/content/jcr_root/apps'
var distBasePath = './target/classes/etc/felibs/'+felibName

/** create the target directories
 *
 */
fs.mkdirsSync(distBasePath)
fs.mkdirsSync(distBasePath+'/css')
fs.mkdirsSync(distBasePath+'/js')

/**
 * compile a single file
 *
 * @param file
 * @returns {{name: string, nameCamelCase: *, nameCapitalCamelCase: string}}
 */
function compileComponent(file){

    console.log("compiling react file: .%s", file)

  var name = file.substring(1, file.lastIndexOf('/')).toLowerCase().split('/').join('-')
  var nameCamelCase = camelcase(name)
  var nameCapitalCamelCase = nameCamelCase.charAt(0).toUpperCase() + nameCamelCase.slice(1)

  // each component needs a unique module name
  var moduleName = 'Cmp'+nameCapitalCamelCase

    // compile the React component and give us a .js
    rollup.rollup({
        input: `${basePath}${file}`,
        plugins: [
            resolve(),
            babel({
                exclude: 'node_modules/**'
            })
        ],
        external: ['react', 'reactDom'],
        paths: {
            react: 'https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react.js',
            reactDom: 'https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react-dom.js'
        },
    }).then( function(bundle) {

        bundle.write({
            format: 'iife',
            name: moduleName,
            file:`${distBasePath}/js/${nameCamelCase}.js`
        }).then( function() {
            updateIndexFiles()
        })
    })

  return { name: name, nameCamelCase: nameCamelCase, nameCapitalCamelCase: nameCapitalCamelCase}
}

/**
 * rewrites the js.txt and css.txt file used to combine the css and js file in the frontend
 *
 */
function updateIndexFiles() {
    var jsFiles = readDirs(distBasePath+'/', distBasePath + '/js', '.js')
    jsFiles.unshift('# auto generated by build')
    console.log(jsFiles)
    fs.writeFileSync(distBasePath+'/js.txt', jsFiles.join('\n'))

    var cssFiles = readDirs(distBasePath+'/', distBasePath + '/css', '.css')
    cssFiles.unshift('# auto generated by build')
    fs.writeFileSync(distBasePath+'/css.txt', cssFiles.join('\n'))
}

/**
 *
 * read a directory and its children and return all the files with the given extension
 *
 * @param basePath
 * @param path
 * @param extFilter
 * @returns {Array}
 */
function readDirs(basePath, path, extFilter) {
    var ret = new Array()
    var files = fs.readdirSync(path)
    files.forEach( function(file) {
        var filePath = path + '/' + file;
        if(filePath.endsWith(extFilter)) {
            ret.push(filePath.slice(basePath.length))
        }
        var stats = fs.statSync(filePath)
        if(stats.isDirectory()) {
            ret = ret.concat(readDirs(basePath, filePath, extFilter))
        }
    })
    return ret;
}

// find all the vue files in this project
var vueFiles = readDirs(basePath, basePath, 'template.js')

// for each of the files compile it
vueFiles.forEach( function(file) {

    compileComponent(file)

})

if(process.argv[3]) {
    console.log('upload')
}
