import {createRequire} from 'node:module';

/**
 * jsdom bootstrap for component mount tests.
 *
 * This module must be imported before `vue-template-compiler` or `vue`: the Vue 2
 * runtime decides once, at module load time, whether it is running in a browser.
 * If something pulls `vue` in before `window`/`document` exist, its patch function
 * becomes a no-op and mounting silently produces no DOM.
 */
const require = createRequire(import.meta.url);
const {JSDOM} = require('jsdom');

const dom = new JSDOM('<!doctype html><html><body></body></html>', {url: 'http://localhost/'});

globalThis.window = dom.window;
globalThis.document = dom.window.document;
Object.defineProperty(globalThis, 'navigator', {value: dom.window.navigator, configurable: true});
globalThis.HTMLElement = dom.window.HTMLElement;
globalThis.Element = dom.window.Element;
globalThis.Node = dom.window.Node;
globalThis.getComputedStyle = (...args) => dom.window.getComputedStyle(...args);

export {dom};
